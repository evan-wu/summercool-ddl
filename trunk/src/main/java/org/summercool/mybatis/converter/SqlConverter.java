package org.summercool.mybatis.converter;

import org.apache.ibatis.executor.statement.StatementHandler;

public interface SqlConverter {

	public String convert(String sql, StatementHandler statementHandler);

}
