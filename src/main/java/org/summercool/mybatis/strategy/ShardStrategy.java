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

	private DataSource mainDataSource;

	private Map<String, DataSource> shardDataSources;

	private String sql;

	private ShardParam shardParam;

	public DataSource getMainDataSource() {
		return mainDataSource;
	}

	public void setMainDataSource(DataSource mainDataSource) {
		this.mainDataSource = mainDataSource;
	}

	public Map<String, DataSource> getShardDataSources() {
		return shardDataSources;
	}

	public void setShardDataSources(Map<String, DataSource> shardDataSources) {
		this.shardDataSources = shardDataSources;
	}

	public String getSql() {
		return sql;
	}

	public void setSql(String sql) {
		this.sql = sql;
	}

	public ShardParam getShardParam() {
		return shardParam;
	}

	public void setShardParam(ShardParam shardParam) {
		this.shardParam = shardParam;
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
