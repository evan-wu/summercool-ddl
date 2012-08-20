package org.summercool.mybatis.spring.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.JdbcTransactionObjectSupport;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.ResourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.summercool.mybatis.util.ReflectionUtils;

/**
 * @Description: 多数据库事务管理实现类
 * @author Kolor
 * @date 2012-8-2 下午4:16:16
 */
public class MultiDataSourceTransactionManager extends AbstractPlatformTransactionManager implements
		ResourceTransactionManager, InitializingBean, BeanFactoryPostProcessor {
	private static final long serialVersionUID = -5155071464588415023L;
	private Class<?> replaceAdviceClass = TransactionInterceptor.class;
	private Class<?> newAdviceClass = ExtTransactionInterceptor.class;
	private AtomicBoolean replaced = new AtomicBoolean();

	/**
	 * Create a new DataSourceTransactionManager instance. A DataSource has to be set to be able to use it.
	 * 
	 * @see #setDataSource
	 */
	public MultiDataSourceTransactionManager() {
		setNestedTransactionAllowed(true);
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		if (!replaced.compareAndSet(false, true)) {
			return;
		}

		String[] names = beanFactory.getBeanDefinitionNames();
		if (names != null) {
			for (String name : names) {
				//
				BeanDefinition beanDef = beanFactory.getBeanDefinition(name);
				if (beanDef != null) {
					if (replaceAdviceClass.getName().equals(beanDef.getBeanClassName())) {
						//
						logger.warn("advice " + replaceAdviceClass + " is replaced by " + newAdviceClass);
						//
						beanDef.setBeanClassName(newAdviceClass.getName());
					}
				}
			}
		}
	}

	@Override
	public Object doGetTransaction() {
		DataSourceTransactionObject txObject = new DataSourceTransactionObject();
		txObject.setSavepointAllowed(isNestedTransactionAllowed());
		ConnectionHolder conHolder = (ConnectionHolder) TransactionSynchronizationManager.getResource(TransactionHolder
				.getDataSource());
		txObject.setConnectionHolder(conHolder, false);

		return txObject;
	}

	/**
	 * This implementation sets the isolation level but ignores the timeout.
	 */
	@Override
	protected void doBegin(Object transaction, TransactionDefinition definition) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
		Connection con = null;

		try {
			if (txObject.getConnectionHolder() == null
					|| txObject.getConnectionHolder().isSynchronizedWithTransaction()) {
				Connection newCon = getThreadDataSource().getConnection();
				if (logger.isDebugEnabled()) {
					logger.debug("Acquired Connection [" + newCon + "] for JDBC transaction");
				}
				txObject.setConnectionHolder(new ConnectionHolder(newCon), true);
			}

			txObject.getConnectionHolder().setSynchronizedWithTransaction(true);
			con = txObject.getConnectionHolder().getConnection();

			Integer previousIsolationLevel = DataSourceUtils.prepareConnectionForTransaction(con, definition);
			txObject.setPreviousIsolationLevel(previousIsolationLevel);

			// Switch to manual commit if necessary. This is very expensive in some JDBC drivers,
			// so we don't want to do it unnecessarily (for example if we've explicitly
			// configured the connection pool to set it already).
			if (con.getAutoCommit()) {
				txObject.setMustRestoreAutoCommit(true);
				if (logger.isDebugEnabled()) {
					logger.debug("Switching JDBC Connection [" + con + "] to manual commit");
				}
				con.setAutoCommit(false);
			}
			
			// txObject.getConnectionHolder().setTransactionActive(true);
			ReflectionUtils.invokeMethod(txObject.getConnectionHolder(), "setTransactionActive",
					new Class[] { boolean.class }, new Object[] { true });

			int timeout = determineTimeout(definition);
			if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
				txObject.getConnectionHolder().setTimeoutInSeconds(timeout);
			}

			// Bind the session holder to the thread.
			if (txObject.isNewConnectionHolder()) {
				TransactionSynchronizationManager.bindResource(getThreadDataSource(), txObject.getConnectionHolder());
			}
		}

		catch (Exception ex) {
			DataSourceUtils.releaseConnection(con, getThreadDataSource());
			throw new CannotCreateTransactionException("Could not open JDBC Connection for transaction", ex);
		}
	}

	public DataSource getThreadDataSource() {
		return TransactionHolder.getDataSource();
	}

	@Override
	protected Object doSuspend(Object transaction) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
		txObject.setConnectionHolder(null);
		ConnectionHolder conHolder = (ConnectionHolder) TransactionSynchronizationManager
				.unbindResource(getThreadDataSource());
		return conHolder;
	}

	@Override
	protected void doResume(Object transaction, Object suspendedResources) {
		ConnectionHolder conHolder = (ConnectionHolder) suspendedResources;
		TransactionSynchronizationManager.bindResource(getThreadDataSource(), conHolder);
	}

	@Override
	protected void doCommit(DefaultTransactionStatus status) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
		Connection con = txObject.getConnectionHolder().getConnection();
		if (status.isDebug()) {
			logger.debug("Committing JDBC transaction on Connection [" + con + "]");
		}
		try {
			con.commit();
		} catch (SQLException ex) {
			throw new TransactionSystemException("Could not commit JDBC transaction", ex);
		}
	}

	@Override
	protected void doRollback(DefaultTransactionStatus status) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
		Connection con = txObject.getConnectionHolder().getConnection();
		if (status.isDebug()) {
			logger.debug("Rolling back JDBC transaction on Connection [" + con + "]");
		}
		try {
			con.rollback();
		} catch (SQLException ex) {
			throw new TransactionSystemException("Could not roll back JDBC transaction", ex);
		}
	}

	@Override
	protected void doSetRollbackOnly(DefaultTransactionStatus status) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
		if (status.isDebug()) {
			logger.debug("Setting JDBC transaction [" + txObject.getConnectionHolder().getConnection()
					+ "] rollback-only");
		}
		txObject.setRollbackOnly();
	}

	@Override
	protected void doCleanupAfterCompletion(Object transaction) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;

		// Remove the connection holder from the thread, if exposed.
		if (txObject.isNewConnectionHolder()) {
			TransactionSynchronizationManager.unbindResource(getThreadDataSource());
		}

		// Reset connection.
		Connection con = txObject.getConnectionHolder().getConnection();
		try {
			if (txObject.isMustRestoreAutoCommit()) {
				con.setAutoCommit(true);
			}
			DataSourceUtils.resetConnectionAfterTransaction(con, txObject.getPreviousIsolationLevel());
		} catch (Throwable ex) {
			logger.debug("Could not reset JDBC Connection after transaction", ex);
		}

		if (txObject.isNewConnectionHolder()) {
			if (logger.isDebugEnabled()) {
				logger.debug("Releasing JDBC Connection [" + con + "] after transaction");
			}
			DataSourceUtils.releaseConnection(con, getThreadDataSource());
		}

		txObject.getConnectionHolder().clear();
	}

	/**
	 * DataSource transaction object, representing a ConnectionHolder. Used as transaction object by
	 * DataSourceTransactionManager.
	 */
	private static class DataSourceTransactionObject extends JdbcTransactionObjectSupport {

		private boolean newConnectionHolder;

		private boolean mustRestoreAutoCommit;

		public void setConnectionHolder(ConnectionHolder connectionHolder, boolean newConnectionHolder) {
			super.setConnectionHolder(connectionHolder);
			this.newConnectionHolder = newConnectionHolder;
		}

		public boolean isNewConnectionHolder() {
			return this.newConnectionHolder;
		}

		@SuppressWarnings("unused")
		public boolean hasTransaction() {
			return (getConnectionHolder() != null && getConnectionHolder().getConnectionHandle() != null);
		}

		public void setMustRestoreAutoCommit(boolean mustRestoreAutoCommit) {
			this.mustRestoreAutoCommit = mustRestoreAutoCommit;
		}

		public boolean isMustRestoreAutoCommit() {
			return this.mustRestoreAutoCommit;
		}

		public void setRollbackOnly() {
			getConnectionHolder().setRollbackOnly();
		}

		public boolean isRollbackOnly() {
			return getConnectionHolder().isRollbackOnly();
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
	}

	@Override
	public Object getResourceFactory() {
		return TransactionHolder.getDataSource();
	}

	public void setNewAdviceClass(Class<?> newAdviceClass) {
		this.newAdviceClass = newAdviceClass;
	}

	public void setReplaceAdviceClass(Class<?> replaceAdviceClass) {
		this.replaceAdviceClass = replaceAdviceClass;
	}
}
