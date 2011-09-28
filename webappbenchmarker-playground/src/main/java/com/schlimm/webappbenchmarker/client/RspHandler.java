package com.schlimm.webappbenchmarker.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

public class RspHandler {
	private byte[] rsp = null;
	
	public synchronized boolean handleResponse(byte[] rsp) {
		this.rsp = rsp;
		this.notify();
		return true;
	}
	
	public synchronized void waitForResponse() throws IOException, ClassNotFoundException {
		while(this.rsp == null) {
			try {
				this.wait();
			} catch (InterruptedException e) {
			}
		}
		
		System.out.println(new ObjectInputStream(new ByteArrayInputStream(rsp)).readObject());

	}
}