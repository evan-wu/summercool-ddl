package org.summercool.mybatis.demo;

import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.summercool.mybatis.demo.service.UserService;

/**
 * 
 * @author Kolor
 */
public class DemoBootstraper {

	public static void main(String[] args) throws InterruptedException {
		ClassPathXmlApplicationContext appCtx = new ClassPathXmlApplicationContext(new String[] {
				"spring/dal-spring.xml", "spring/service-spring.xml" });

		UserService userService = appCtx.getBean("userService", UserService.class);
		userService.testAddUsers();
		
		Thread.sleep(5000L);
	}

}
