package com.mycompany.springapp.scope.extension;

import org.springframework.beans.factory.config.Scope;
import org.springframework.web.context.request.AbstractRequestAttributesScope;
import org.springframework.web.context.request.RequestAttributes;

public class MyCustomScope extends AbstractRequestAttributesScope implements Scope {

	@Override
	protected int getScope() {
		return RequestAttributes.SCOPE_REQUEST;
	}

	/**
	 * There is no conversation id concept for a request, so this method
	 * returns <code>null</code>.
	 */
	public String getConversationId() {
		return null;
	}

}
