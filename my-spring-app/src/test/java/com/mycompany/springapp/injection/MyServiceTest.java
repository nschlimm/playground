package com.mycompany.springapp.injection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.Assert;

@ContextConfiguration("/test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class MyServiceTest {
	
	@Autowired
	private MyService myService;
	
	@Test
	public void testDirectInjection() {
		
		Assert.notNull(myService.getMyDaoByExpression());
		
	}
	                              

}
