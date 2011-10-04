package com.schlimm.webappbenchmarker.client;

import java.io.IOException;

public interface WBClient {
	
	void send(byte[] data, BenchmarkRspHandler handler) throws IOException;

}
