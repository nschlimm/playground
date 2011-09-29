package com.schlimm.webappbenchmarker.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.DecimalFormat;

import com.schlimm.webappbenchmarker.statistic.Statistics;

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
		Statistics statistics = (Statistics) object[0];
		 DecimalFormat df = new DecimalFormat("#.####");
		System.out.println(String.format("%1$-20s %2$-15s %3$-11s %4$-10s %5$-10s %6$-10s", df.format(statistics.mean()), df.format(statistics.stddev()), statistics.getJitTimeBeforeHarness(), statistics.getJitTimeAfterHarness(), statistics.getClassesLoadedBeforeHarness(), statistics.getClassesLoadedAfterHarness()));
	}
}