package com.mycompany.springapp.springcdi;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("/test-decorator-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DecoratorTestCase {

	@Autowired
	private MyService service;
	
	@Test
	public void testHelloWorld() {
		
		Assert.assertTrue(service.sayHello().equals("Hellotransactionsecurity"));
		
	}
}
