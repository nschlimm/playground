package com.mycompany.jeeapp;

import java.util.Arrays;

import javassist.util.proxy.ProxyObject;

import javax.inject.Inject;

import org.jboss.arquillian.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ByteArrayAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.mycompany.jeeapp.decorator.MyDecorator;
import com.mycompany.jeeapp.decorator.MyDecorator2;
import com.mycompany.jeeapp.decorator.MyDelegate;
import com.mycompany.jeeapp.decorator.MyServiceInterface;

/**
 * http://amitstechblog.wordpress.com/2011/04/29/glassfish-source-code/
 * @author nschlimm
 *
 */
@RunWith(Arquillian.class)
public class DelegateAnnotationTest {

	// must be decorator
	@Inject
	private MyServiceInterface decoratedInterface;
	
	@Inject
	private MyDelegate myDelegate;

	// Geht auch nicht: Unsatisfied dependencies for type [MyDecorator] with qualifiers [@Default]
//	@Inject
//	private MyDecorator myDecorator;
	
	@Deployment
	public static JavaArchive createTestArchive() {
		return ShrinkWrap.create(JavaArchive.class, "test.jar").addClasses(MyServiceInterface.class,MyDelegate.class, MyDecorator.class,
	            MyDecorator2.class).addAsManifestResource(
	                    new ByteArrayAsset("<decorators><class>com.mycompany.jeeapp.decorator.MyDecorator</class><class>com.mycompany.jeeapp.decorator.MyDecorator2</class></decorators>".getBytes()), ArchivePaths.create("beans.xml"));
	}

	/**
	 * If you inject the decorated interface somewhere by interface type, then the proxy instance is injected
	 */
	@Test
	public void testDecoratorInterfaceInjection() {
		Assert.assertTrue(decoratedInterface.getHelloWorld().equals("Hello"));
		Assert.assertTrue(Arrays.asList(decoratedInterface.getClass().getInterfaces()).contains(ProxyObject.class));
	}

	/**
	 * If you inject the delegate somewhere, then the proxy instance is injected
	 */
	@Test
	public void testDelegateInjection() {
		Assert.assertTrue(myDelegate.getHelloWorld().equals("Hello"));
		Assert.assertTrue(Arrays.asList(myDelegate.getClass().getInterfaces()).contains(ProxyObject.class));
	}
	
//	/**
//	 * If you inject the decorator somewhere, then the decorator class instance is injected
//	 */
//	@Test
//	public void testDecoratorInjection() {
//		Assert.assertTrue(myDecorator.getHelloWorld().equals("Hello"));
//	}
	
}
