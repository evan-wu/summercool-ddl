/**
 * 
 */
package org.summercool.mybatis.strategy;

import java.util.Map;

import javax.sql.DataSource;

import org.summercool.mybatis.ShardParam;

/**
 * 分表策略接口
 * 
 * @author sean.he
 * 
 */
public abstract class ShardStrategy {

	private static final ThreadLocal<DataSource> mainDataSource = new ThreadLocal<DataSource>();
	private static final ThreadLocal<Map<String, DataSource>> shardDataSources = new ThreadLocal<Map<String,DataSource>>();
	private static final ThreadLocal<String> sql = new ThreadLocal<String>();
	private static final ThreadLocal<ShardParam> shardParam= new ThreadLocal<ShardParam>();

	public DataSource getMainDataSource() {
		return mainDataSource.get();
	}

	public void setMainDataSource(DataSource mainDataSource) {
		ShardStrategy.mainDataSource.set(mainDataSource);
	}

	public Map<String, DataSource> getShardDataSources() {
		return shardDataSources.get();
	}

	public void setShardDataSources(Map<String, DataSource> shardDataSources) {
		ShardStrategy.shardDataSources.set(shardDataSources);
	}

	public String getSql() {
		return sql.get();
	}

	public void setSql(String sql) {
		ShardStrategy.sql.set(sql);
	}

	public ShardParam getShardParam() {
		return shardParam.get();
	}

	public void setShardParam(ShardParam shardParam) {
		ShardStrategy.shardParam.set(shardParam);
	}

	public abstract DataSource getTargetDataSource();

	/**
	 * 得到实际表名
	 * 
	 * @param baseTableName
	 *            逻辑表名,一般是没有前缀或者是后缀的表名
	 * @param params
	 *            mybatis执行某个statement时使用的参数
	 * @param mapperId
	 *            mybatis配置的statement id
	 * @return
	 */
	public abstract String getTargetSql();
}
