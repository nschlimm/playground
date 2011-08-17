package com.mycompany.springapp.scope;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.Assert;


@ContextConfiguration("/test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class MyScopeBeanTest {
	
	@Autowired
	private MyScopeBean myScopeBean;
	
	@Test
	public void testBeanScopes() {
		Assert.isTrue(myScopeBean.getMyCustomScopedService().getName().equals("Test"));
		Assert.isTrue(myScopeBean.getMySessionScopedService().getName().equals("Test"));
//		Assert.isTrue(myScopeBean.getMyApplicationScopedService().getName().equals("Name"));
//		Assert.isTrue(myScopeBean.getMyGlobalSessionScopedService().getName().equals("Name"));
//		Assert.isTrue(myScopeBean.getMyPrototypeScopedService().getName().equals("Name"));
//		Assert.isTrue(myScopeBean.getMyRequestScopedService().getName().equals("Name"));
//		Assert.isTrue(myScopeBean.getMySingletonScopedService().getName().equals("Name"));
//		Assert.isTrue(myScopeBean.getMyThreadScopedService().getName().equals("Name"));
	}

}
