package org.summercool.mybatis.spring.support;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.TransactionAttribute;

/**
 * @Description: 事务信息包装类
 * @author Kolor
 * @date 2012-8-3 下午2:07:47
 */
public class TransactionInfoWrap {
	private final PlatformTransactionManager transactionManager;

	private final TransactionAttribute transactionAttribute;

	private final String joinpointIdentification;

	private TransactionStatus transactionStatus;

	public TransactionInfoWrap(PlatformTransactionManager transactionManager,
			TransactionAttribute transactionAttribute, String joinpointIdentification) {
		this.transactionManager = transactionManager;
		this.transactionAttribute = transactionAttribute;
		this.joinpointIdentification = joinpointIdentification;
	}

	public PlatformTransactionManager getTransactionManager() {
		return this.transactionManager;
	}

	public TransactionAttribute getTransactionAttribute() {
		return this.transactionAttribute;
	}

	/**
	 * Return a String representation of this joinpoint (usually a Method call) for use in logging.
	 */
	public String getJoinpointIdentification() {
		return this.joinpointIdentification;
	}

	public void newTransactionStatus(TransactionStatus status) {
		this.transactionStatus = status;
	}

	public TransactionStatus getTransactionStatus() {
		return this.transactionStatus;
	}

	/**
	 * Return whether a transaction was created by this aspect, or whether we just have a placeholder to keep
	 * ThreadLocal stack integrity.
	 */
	public boolean hasTransaction() {
		return (this.transactionStatus != null);
	}

	@Override
	public String toString() {
		return this.transactionAttribute.toString();
	}

	public TransactionInfoWrap newCopy() {
		return new TransactionInfoWrap(this.transactionManager, this.transactionAttribute, this.joinpointIdentification);
	}
}
