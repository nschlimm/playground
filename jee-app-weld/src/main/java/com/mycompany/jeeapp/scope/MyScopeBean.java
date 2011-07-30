package com.mycompany.jeeapp.scope;

import javax.enterprise.inject.New;
import javax.inject.Inject;

import com.mycompany.jeeapp.scope.extension.MyCustomScopeService;

public class MyScopeBean {
	
	@Inject @New
	private MyApplicationService applicationServiceWithNew;
	
	@Inject
	private MyApplicationService applicationService;

	@Inject
	private MyConversationService conversationService;
	
	@Inject
	private MyDefaultService defaultService;
	
	@Inject
	private MyRequestService requestService;
	
	@Inject
	private MySessionService sessionService;
	
	@Inject
	private MySingletonService singletonService;
	
	@Inject
	private MyCustomScopeService customScopeService;

	public MyApplicationService getApplicationServiceWithNew() {
		return applicationServiceWithNew;
	}

	public MyApplicationService getApplicationService() {
		return applicationService;
	}

	public MyConversationService getConversationService() {
		return conversationService;
	}

	public MyDefaultService getDefaultService() {
		return defaultService;
	}

	public MyRequestService getRequestService() {
		return requestService;
	}

	public MySessionService getSessionService() {
		return sessionService;
	}

	public MySingletonService getSingletonService() {
		return singletonService;
	}

	public MyCustomScopeService getCustomScopeService() {
		return customScopeService;
	}
	
}
