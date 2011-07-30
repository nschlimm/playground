package com.mycompany.springapp.scope;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.SessionScope;

public class SetupSession extends SessionScope implements InitializingBean {

	public void afterPropertiesSet() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpSession session = new MockHttpSession();
		request.setSession(session);
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
				request));
	}

}