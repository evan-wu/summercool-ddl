package org.summercool.mybatis.spring.support;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.DelegatingTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.CallbackPreferringPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 对事务拦截器org.springframework.transaction.interceptor.TransactionInterceptor的扩展，以支持分库事务控制。
 * @author Kolor
 */
@SuppressWarnings("serial")
public class ExtTransactionInterceptor extends TransactionInterceptor implements MethodInterceptor, Serializable {

	/**
	 * Create a new TransactionInterceptor. <p>Transaction manager and transaction attributes still need to be set.
	 * 
	 * @see #setTransactionManager
	 * @see #setTransactionAttributes(java.util.Properties)
	 * @see #setTransactionAttributeSource(TransactionAttributeSource)
	 */
	public ExtTransactionInterceptor() {
	}

	/**
	 * Create a new TransactionInterceptor.
	 * 
	 * @param ptm
	 *        the transaction manager to perform the actual transaction management
	 * @param attributes
	 *        the transaction attributes in properties format
	 * @see #setTransactionManager
	 * @see #setTransactionAttributes(java.util.Properties)
	 */
	public ExtTransactionInterceptor(PlatformTransactionManager ptm, Properties attributes) {
		super(ptm, attributes);
	}

	/**
	 * Create a new TransactionInterceptor.
	 * 
	 * @param ptm
	 *        the transaction manager to perform the actual transaction management
	 * @param tas
	 *        the attribute source to be used to find transaction attributes
	 * @see #setTransactionManager
	 * @see #setTransactionAttributeSource(TransactionAttributeSource)
	 */
	public ExtTransactionInterceptor(PlatformTransactionManager ptm, TransactionAttributeSource tas) {
		super(ptm, tas);
	}

	private void holdTransactionDef(PlatformTransactionManager tm, TransactionAttribute txAttr,
			final String joinpointIdentification) {
		// If no name specified, apply method identification as transaction name.
		if (txAttr != null && txAttr.getName() == null) {
			txAttr = new DelegatingTransactionAttribute(txAttr) {
				@Override
				public String getName() {
					return joinpointIdentification;
				}
			};
		}

		TransactionHolder.setTransactionInfo(new TransactionInfoWrap(tm, txAttr, joinpointIdentification));
	}

	public Object invoke(final MethodInvocation invocation) throws Throwable {
		// Work out the target class: may be <code>null</code>.
		// The TransactionAttributeSource should be passed the target class
		// as well as the method, which may be from an interface.
		Class<?> targetClass = (invocation.getThis() != null ? AopUtils.getTargetClass(invocation.getThis()) : null);

		// If the transaction attribute is null, the method is non-transactional.
		final TransactionAttribute txAttr = getTransactionAttributeSource().getTransactionAttribute(
				invocation.getMethod(), targetClass);
		final PlatformTransactionManager tm = determineTransactionManager(txAttr);
		final String joinpointIdentification = methodIdentification(invocation.getMethod(), targetClass);

		if (txAttr == null || !(tm instanceof CallbackPreferringPlatformTransactionManager)) {
			try {
				holdTransactionDef(tm, txAttr, joinpointIdentification);
				Object retVal = null;
				try {
					// This is an around advice: Invoke the next interceptor in the chain.
					// This will normally result in a target object being invoked.
					retVal = invocation.proceed();
				} catch (Throwable ex) {
					// target invocation exception
					completeTransactionAfterThrowing(ex);
					throw ex;
				}
				commitTransactionAfterReturning();
				return retVal;
			} finally {
				cleanupTransactionInfo();
			}
		} else {
			// It's a CallbackPreferringPlatformTransactionManager: pass a TransactionCallback in.
			try {
				Object result = ((CallbackPreferringPlatformTransactionManager) tm).execute(txAttr,
						new TransactionCallback<Object>() {
							public Object doInTransaction(TransactionStatus status) {
								holdTransactionDef(tm, txAttr, joinpointIdentification);
								try {
									return invocation.proceed();
								} catch (Throwable ex) {
									// if (txAttr.rollbackOn(ex)) {
									if (attrRollback(ex)) {
										// A RuntimeException: will lead to a rollback.
										if (ex instanceof RuntimeException) {
											throw (RuntimeException) ex;
										} else {
											throw new ThrowableHolderException(ex);
										}
									} else {
										// A normal return value: will lead to a commit.
										return new ThrowableHolder(ex);
									}
								} finally {
									cleanupTransactionInfo();
								}
							}
						});

				// Check result: It might indicate a Throwable to rethrow.
				if (result instanceof ThrowableHolder) {
					throw ((ThrowableHolder) result).getThrowable();
				} else {
					return result;
				}
			} catch (ThrowableHolderException ex) {
				throw ex.getCause();
			}
		}
	}

	private void cleanupTransactionInfo() {
		TransactionHolder.clearAll();
	}

	private void commitTransactionAfterReturning() {
		Map<DataSource, LinkedList<TransactionInfoWrap>> txTree = TransactionHolder.getTxTree();
		if (txTree != null) {
			for (Map.Entry<DataSource, LinkedList<TransactionInfoWrap>> entry : txTree.entrySet()) {
				Iterator<TransactionInfoWrap> itor = entry.getValue().descendingIterator();
				//
				while (itor.hasNext()) {
					TransactionInfoWrap txInfo = itor.next();
					// commit
					TransactionInfo tx = new TransactionInfo(txInfo.getTransactionManager(),
							txInfo.getTransactionAttribute(), txInfo.getJoinpointIdentification());
					tx.newTransactionStatus(txInfo.getTransactionStatus());
					//
					prepareCommitOrRollback(txInfo);
					//
					commitTransactionAfterReturning(tx);
				}
			}
		}
	}

	/**
	 * 
	 * @author Kolor
	 * @param @param txInfo
	 * @return void
	 */
	private void prepareCommitOrRollback(TransactionInfoWrap txInfo) {
		DefaultTransactionStatus status = (DefaultTransactionStatus) txInfo.getTransactionStatus();
		if (!TransactionSynchronizationManager.isSynchronizationActive() && status.isNewSynchronization()) {
			//
			TransactionSynchronizationManager.setActualTransactionActive(status.hasTransaction());
			TransactionDefinition definition = txInfo.getTransactionAttribute();
			TransactionSynchronizationManager
					.setCurrentTransactionIsolationLevel((definition.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT) ? definition
							.getIsolationLevel() : null);
			TransactionSynchronizationManager.setCurrentTransactionReadOnly(definition.isReadOnly());
			TransactionSynchronizationManager.setCurrentTransactionName(definition.getName());
			//
			TransactionSynchronizationManager.initSynchronization();
		}
		//
		TransactionHolder.setDataSource(TransactionHolder.removeStatusDS(status));
	}

	private void completeTransactionAfterThrowing(Throwable t) {
		Map<DataSource, LinkedList<TransactionInfoWrap>> txTree = TransactionHolder.getTxTree();
		if (txTree != null) {
			for (Map.Entry<DataSource, LinkedList<TransactionInfoWrap>> entry : txTree.entrySet()) {
				Iterator<TransactionInfoWrap> itor = entry.getValue().descendingIterator();
				//
				while (itor.hasNext()) {
					TransactionInfoWrap txInfo = itor.next();
					// rollback
					TransactionInfo tx = new TransactionInfo(txInfo.getTransactionManager(),
							txInfo.getTransactionAttribute(), txInfo.getJoinpointIdentification());
					tx.newTransactionStatus(txInfo.getTransactionStatus());
					//
					prepareCommitOrRollback(txInfo);
					//
					completeTransactionAfterThrowing(tx, t);
				}
			}
		}
	}

	private boolean attrRollback(Throwable t) {
		Map<DataSource, LinkedList<TransactionInfoWrap>> txTree = TransactionHolder.getTxTree();
		if (txTree != null) {
			for (Map.Entry<DataSource, LinkedList<TransactionInfoWrap>> entry : txTree.entrySet()) {
				Iterator<TransactionInfoWrap> itor = entry.getValue().descendingIterator();
				//
				while (itor.hasNext()) {
					TransactionInfoWrap txInfo = itor.next();
					// rollback
					txInfo.getTransactionAttribute().rollbackOn(t);
				}
			}
		}
		return true;
	}

	// ---------------------------------------------------------------------
	// Serialization support
	// ---------------------------------------------------------------------

	private void writeObject(ObjectOutputStream oos) throws IOException {
		// Rely on default serialization, although this class itself doesn't carry state anyway...
		oos.defaultWriteObject();

		// Deserialize superclass fields.
		oos.writeObject(getTransactionManagerBeanName());
		oos.writeObject(getTransactionManager());
		oos.writeObject(getTransactionAttributeSource());
		oos.writeObject(getBeanFactory());
	}

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		// Rely on default serialization, although this class itself doesn't carry state anyway...
		ois.defaultReadObject();

		// Serialize all relevant superclass fields.
		// Superclass can't implement Serializable because it also serves as base class
		// for AspectJ aspects (which are not allowed to implement Serializable)!
		setTransactionManagerBeanName((String) ois.readObject());
		setTransactionManager((PlatformTransactionManager) ois.readObject());
		setTransactionAttributeSource((TransactionAttributeSource) ois.readObject());
		setBeanFactory((BeanFactory) ois.readObject());
	}

	/**
	 * Internal holder class for a Throwable, used as a return value from a TransactionCallback (to be subsequently
	 * unwrapped again).
	 */
	private static class ThrowableHolder {

		private final Throwable throwable;

		public ThrowableHolder(Throwable throwable) {
			this.throwable = throwable;
		}

		public final Throwable getThrowable() {
			return this.throwable;
		}
	}

	/**
	 * Internal holder class for a Throwable, used as a RuntimeException to be thrown from a TransactionCallback (and
	 * subsequently unwrapped again).
	 */
	private static class ThrowableHolderException extends RuntimeException {

		public ThrowableHolderException(Throwable throwable) {
			super(throwable);
		}

		@Override
		public String toString() {
			return getCause().toString();
		}
	}

}
