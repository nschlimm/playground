package com.schlimm.webappbenchmarker.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

import com.schlimm.webappbenchmarker.statistic.Average;

public class BenchmarkRspHandler {
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
		Object[] object = (Object[]) new ObjectInputStream(new ByteArrayInputStream(rsp)).readObject();
		Average average = (Average) object[0];
		System.out.println(average.mean() + ";" + average.stddev());

	}
}