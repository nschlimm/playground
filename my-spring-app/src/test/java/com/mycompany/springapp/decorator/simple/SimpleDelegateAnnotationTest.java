package com.mycompany.springapp.decorator.simple;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.Assert;



@ContextConfiguration("/test-context-decorator-simple.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class SimpleDelegateAnnotationTest {

	// must be decorator
	@Autowired
	private MyDecorator decorator;

	// must be delegate
	@Autowired 
	private MyDelegate delegate;
	
	// must be decorator
	@Autowired 
	private MyServiceInterface decoratedInterface;
	
	/**
	 * If you inject the delegate in the decorator by class, then the delegate class instance is injected
	 */
	@Test
	public void testDelegateAutowiringInDecorator_DelegateClass() {
		Assert.isTrue(decorator.getDelegateClass().getClass().equals(MyDelegate.class));
	}
	
	/**
	 * If you inject the delegate in the decorator by interface type, then the delegate class instance is injected
	 */
	@Test
	public void testDelegateAutowiringInDecorator_DelegateInterface() {
		Assert.isTrue(decorator.getDelegateInterface().getClass().equals(MyDelegate.class));
	}
	
	/**
	 * If you inject the delegate somewhere by class, then the delegate class instance is injected
	 */
	@Test
	public void testDecoratorClassInjection() {
		Assert.isTrue(delegate.getClass().equals(MyDelegate.class));
	}
	
	/**
	 * If you inject the decorated interface somewhere by interface type, then the decorator class instance is injected
	 * If you inject the MyServiceInterface interface into the decorator, then the delegate instance gets injected
	 * If you inject the MyDelegate class into the decorator, then the delegate instance gets injected
	 */
	@Test
	public void testDecoratorInterfaceInjection() {
		Assert.isTrue(decoratedInterface.getClass().equals(MyDecorator.class));
		Assert.isTrue(decoratedInterface.getDelegateClass().getClass().equals(MyDelegate.class));
		Assert.isTrue(decoratedInterface.getDelegateInterface().getClass().equals(MyDelegate.class));
	}
	
}
