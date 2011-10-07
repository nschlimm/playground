package com.schlimm.webappbenchmarker.command.clientscenarios.threading;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.schlimm.webappbenchmarker.client.SimpleRspHandler;
import com.schlimm.webappbenchmarker.client.WBClient;
import com.schlimm.webappbenchmarker.command.ClientCommand;
import com.schlimm.webappbenchmarker.command.Testscenario;
import com.schlimm.webappbenchmarker.protocol.StandardJavaSerialization;

public class ContentionScenario extends Testscenario {

	public ContentionScenario(WBClient client) {
		super(client);
	}

	private class Contentions implements Callable<Long> {

		private ClientCommand command;
		
		public Contentions(ClientCommand command) {
			super();
			this.command = command;
		}

		@Override
		public Long call() throws Exception {
			while(true) {
				if (Thread.currentThread().isInterrupted()) {
					break;
				}
				SimpleRspHandler rspHandler = new SimpleRspHandler();
				client.send(new StandardJavaSerialization().toByteArray(command), rspHandler);
				rspHandler.waitForResponse();
			}
			return new Long(0);
		}
		
	}
	
	@SuppressWarnings({ "static-access" })
	@Override
	public void execute(Object... args) {
		ExecutorService pool = Executors.newFixedThreadPool(4);
		pool.submit(new Contentions(new ClientCommand((String)args[0])));
		pool.submit(new Contentions(new ClientCommand((String)args[0])));
		try {
			Thread.currentThread().sleep(30000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		pool.shutdownNow();
	}

}
