package org.summercool.mybatis.strategy;

import javax.sql.DataSource;

public class NoShardStrategy extends ShardStrategy {

	public static final NoShardStrategy INSTANCE = new NoShardStrategy();

	@Override
	public String getTargetSql() {
		return getSql();
	}

	@Override
	public DataSource getTargetDataSource() {
		return getMainDataSource();
	}

}
