package org.summercool.mybatis.spring.support;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.transaction.TransactionStatus;

/**
 * @Description: 事务处理相关数据持有者
 * @author Kolor
 * @date 2012-8-3 下午1:54:29
 */
public class TransactionHolder {
	private static ThreadLocal<Map<DataSource, LinkedList<TransactionInfoWrap>>> tranTreeHolder = new ThreadLocal<Map<DataSource, LinkedList<TransactionInfoWrap>>>();
	private static ThreadLocal<DataSource> dsHolder = new ThreadLocal<DataSource>();
	private static ThreadLocal<TransactionInfoWrap> txInfoHolder = new ThreadLocal<TransactionInfoWrap>();
	private static ThreadLocal<Map<TransactionStatus, DataSource>> statusDSHolder = new ThreadLocal<Map<TransactionStatus, DataSource>>();

	static void addStatusDS(TransactionStatus status, DataSource ds) {
		Map<TransactionStatus, DataSource> map = statusDSHolder.get();
		if (map == null) {
			map = new HashMap<TransactionStatus, DataSource>();
			statusDSHolder.set(map);
		}
		//
		map.put(status, ds);
	}

	static DataSource removeStatusDS(TransactionStatus status) {
		Map<TransactionStatus, DataSource> map = statusDSHolder.get();
		if (map != null) {
			DataSource ds = map.remove(status);
			if (map.isEmpty()) {
				statusDSHolder.remove();
			}
			return ds;
		}
		return null;
	}

	static void setDataSource(DataSource ds) {
		dsHolder.set(ds);
	}

	public static DataSource getDataSource() {
		return dsHolder.get();
	}

	static void addTxInfo2Tree(DataSource ds, TransactionInfoWrap txInfo) {
		Map<DataSource, LinkedList<TransactionInfoWrap>> map = tranTreeHolder.get();
		if (map == null) {
			map = new LinkedHashMap<DataSource, LinkedList<TransactionInfoWrap>>();
			tranTreeHolder.set(map);
		}
		//
		LinkedList<TransactionInfoWrap> subTree = map.get(ds);
		if (subTree == null) {
			subTree = new LinkedList<TransactionInfoWrap>();
			map.put(ds, subTree);
		}
		//
		subTree.add(txInfo);
	}

	static Map<DataSource, LinkedList<TransactionInfoWrap>> getTxTree() {
		return tranTreeHolder.get();
	}

	static void setTransactionInfo(TransactionInfoWrap txInfo) {
		txInfoHolder.set(txInfo);
	}

	static TransactionInfoWrap getTransactionInfo() {
		return txInfoHolder.get();
	}

	static void clearAll() {
		tranTreeHolder.remove();
		dsHolder.remove();
		txInfoHolder.remove();
		statusDSHolder.remove();
	}
}
