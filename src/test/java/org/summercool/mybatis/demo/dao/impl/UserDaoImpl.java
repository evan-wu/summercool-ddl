package org.summercool.mybatis.demo.dao.impl;

import org.summercool.mybatis.ShardParam;
import org.summercool.mybatis.demo.dao.UserDao;
import org.summercool.mybatis.demo.dao.entity.UserEntity;
import org.summercool.mybatis.spring.support.SqlSessionDaoSupport;

/**
 *  
 * @author Kolor
 */
public class UserDaoImpl extends SqlSessionDaoSupport implements UserDao {

	public boolean insertUser(UserEntity user) {
		ShardParam shardParam = new ShardParam("Shard-User", user.getId(), user);
		
		return getSqlSession().insert("NS-User.insertUser", shardParam) > 0;
	}

}
