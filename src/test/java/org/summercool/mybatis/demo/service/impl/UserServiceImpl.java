package org.summercool.mybatis.demo.service.impl;

import org.summercool.mybatis.demo.dao.UserDao;
import org.summercool.mybatis.demo.dao.entity.UserEntity;
import org.summercool.mybatis.demo.service.UserService;

/**
 *  
 * @author Kolor
 */
public class UserServiceImpl implements UserService {

	private UserDao userDao;
	
	public void testAddUsers() {
		UserEntity user1 = new UserEntity();
		user1.setId(1);
		user1.setName("1");
		//
		userDao.insertUser(user1);
		
		//
		UserEntity user2 = new UserEntity();
		user2.setId(2);
		user2.setName("2");
		//
		userDao.insertUser(user2);

		//
		UserEntity user3 = new UserEntity();
		user3.setId(200);
		user3.setName("200");
		//
		userDao.insertUser(user3);

		//
		UserEntity user4 = new UserEntity();
		user4.setId(201);
		user4.setName("201");
		//
		userDao.insertUser(user4);
	}

	public void setUserDao(UserDao userDao) {
		this.userDao = userDao;
	}
}
