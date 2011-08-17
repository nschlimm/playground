package com.mycompany.springapp.scope;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mycompany.springapp.scope.extension.MyCustomScopedService;

@Component
public class MyScopeBean {

	@Autowired
	private MyApplicationScopedService myApplicationScopedService;

	@Autowired
	private MyGlobalSessionScopedService myGlobalSessionScopedService;

	@Autowired
	private MyPrototypeScopedService myPrototypeScopedService;

	@Autowired
	private MyRequestScopedService myRequestScopedService;

	@Autowired
	private MySessionScopedService mySessionScopedService;

	@Autowired
	private MySingletonScopedService mySingletonScopedService;

	@Autowired
	private MyThreadScopedService myThreadScopedService;

	@Autowired
	private MyCustomScopedService myCustomScopedService;

	public MyApplicationScopedService getMyApplicationScopedService() {
		return myApplicationScopedService;
	}

	public MyGlobalSessionScopedService getMyGlobalSessionScopedService() {
		return myGlobalSessionScopedService;
	}

	public MyPrototypeScopedService getMyPrototypeScopedService() {
		return myPrototypeScopedService;
	}

	public MyRequestScopedService getMyRequestScopedService() {
		return myRequestScopedService;
	}

	public MySessionScopedService getMySessionScopedService() {
		return mySessionScopedService;
	}

	public MySingletonScopedService getMySingletonScopedService() {
		return mySingletonScopedService;
	}

	public MyThreadScopedService getMyThreadScopedService() {
		return myThreadScopedService;
	}

	public MyCustomScopedService getMyCustomScopedService() {
		return myCustomScopedService;
	}

	public void validateReferences() {
		myApplicationScopedService.getName();
		myCustomScopedService.getName();
		myGlobalSessionScopedService.getName();
		myPrototypeScopedService.getName();
		myRequestScopedService.getName();
		mySessionScopedService.getName();
		mySingletonScopedService.getName();
		myThreadScopedService.getName();
	}

}
