package com.mycompany.jeeapp.scope.extension;

import org.jboss.weld.context.http.HttpRequestContext;
import org.jboss.weld.context.http.HttpRequestContextImpl;
import java.lang.Class;

public class MyCustomScope extends HttpRequestContextImpl implements HttpRequestContext {

	public Class<CustomScoped> getScope() {
		return CustomScoped.class;
	}
	
}
