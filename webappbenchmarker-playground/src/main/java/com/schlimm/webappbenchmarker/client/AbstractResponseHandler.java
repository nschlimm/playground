package com.schlimm.webappbenchmarker.client;

public abstract class AbstractResponseHandler {

	abstract public boolean handleResponse(byte[] rsp);
	abstract public void waitForResponse();
	
}
