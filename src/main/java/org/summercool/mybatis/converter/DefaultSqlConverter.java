package org.summercool.mybatis.converter;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.summercool.mybatis.spring.support.StrategyHolder;
import org.summercool.mybatis.strategy.NoShardStrategy;
import org.summercool.mybatis.strategy.ShardStrategy;

public class DefaultSqlConverter implements SqlConverter {

	public String convert(String sql, StatementHandler statementHandler) {
		ShardStrategy strategy = StrategyHolder.getShardStrategy();
		if (strategy == null || strategy instanceof NoShardStrategy) {
			return sql;
		}
		return strategy.getTargetSql();
	}

}
