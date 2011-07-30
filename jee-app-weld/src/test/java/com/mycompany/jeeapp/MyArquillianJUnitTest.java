package com.mycompany.jeeapp;

import javax.inject.Inject;

import junit.framework.Assert;

import org.jboss.arquillian.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.mycompany.jeeapp.scope.MyApplicationService;
import com.mycompany.jeeapp.scope.MyConversationService;
import com.mycompany.jeeapp.scope.MyDefaultService;
import com.mycompany.jeeapp.scope.MyRequestService;
import com.mycompany.jeeapp.scope.MyScopeBean;
import com.mycompany.jeeapp.scope.MySessionService;
import com.mycompany.jeeapp.scope.MySingletonService;
import com.mycompany.jeeapp.scope.extension.MyCustomScopeService;

@RunWith(Arquillian.class)
public class MyArquillianJUnitTest {

	@Inject
	private MyScopeBean myScopeBean;

	@Deployment
	public static JavaArchive createTestArchive() {
		return ShrinkWrap
				.create(JavaArchive.class, "test.jar")
				.addClasses(MyScopeBean.class,MyApplicationService.class,
						MyConversationService.class, MyDefaultService.class,
						MyRequestService.class, MySessionService.class,
						MySingletonService.class, MyCustomScopeService.class)
				.addAsManifestResource(EmptyAsset.INSTANCE,
						ArchivePaths.create("beans.xml"));
	}

	@Test
	public void testScopedBeans() {
		Assert.assertTrue(myScopeBean.getApplicationService().getSomeName()
				.equals("myName"));
		Assert.assertTrue(myScopeBean.getApplicationServiceWithNew().getSomeName()
				.equals("myName"));
		Assert.assertTrue(myScopeBean.getCustomScopeService().getSomeName()
				.equals("myName"));
		Assert.assertTrue(myScopeBean.getDefaultService().getSomeName()
				.equals("myName"));
		Assert.assertTrue(myScopeBean.getRequestService().getSomeName()
				.equals("myName"));
		Assert.assertTrue(myScopeBean.getSessionService().getSomeName()
				.equals("myName"));
		Assert.assertTrue(myScopeBean.getSingletonService().getSomeName()
				.equals("myName"));
	}

}
