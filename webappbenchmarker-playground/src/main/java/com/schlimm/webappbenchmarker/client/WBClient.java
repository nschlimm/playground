package com.schlimm.webappbenchmarker.client;

public interface WBClient {
	
	void send(byte[] data, AbstractResponseHandler handler);

}
