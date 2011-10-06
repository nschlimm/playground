package com.schlimm.webappbenchmarker.command.clientscenarios.threading;

import java.util.concurrent.Callable;

import com.schlimm.webappbenchmarker.client.SimpleRspHandler;
import com.schlimm.webappbenchmarker.client.WBClient;
import com.schlimm.webappbenchmarker.command.ClientCommand;
import com.schlimm.webappbenchmarker.command.Testscenario;
import com.schlimm.webappbenchmarker.protocol.StandardJavaSerialization;

public class ContentionScenario extends Testscenario {

	public ContentionScenario(WBClient client) {
		super(client);
	}

	@SuppressWarnings({ "unused", "rawtypes" })
	private class Contentions implements Callable {

		private ClientCommand command;
		
		public Contentions(ClientCommand command) {
			super();
			this.command = command;
		}

		@Override
		public Object call() throws Exception {
			while(!Thread.currentThread().isInterrupted()) {
				SimpleRspHandler rspHandler = new SimpleRspHandler();
				client.send(new StandardJavaSerialization().toByteArray(command), rspHandler);
				rspHandler.waitForResponse();
			}
			return null;
		}
		
	}
	
	@Override
	public void execute(Object... args) {
		
	}

}
