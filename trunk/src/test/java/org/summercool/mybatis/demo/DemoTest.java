package org.summercool.mybatis.demo;

import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.summercool.mybatis.demo.service.UserService;

/**
 * 
 * @author Kolor
 */
public class DemoTest {

	private ClassPathXmlApplicationContext appCtx;

	@Before
	public void init() {
		appCtx = new ClassPathXmlApplicationContext(
				new String[] { "spring/dal-spring.xml", "spring/service-spring.xml" });
	}

	@Test
	public void test_add() {
		UserService userService = appCtx.getBean("userService", UserService.class);
		try {
			userService.testAddUsers();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
