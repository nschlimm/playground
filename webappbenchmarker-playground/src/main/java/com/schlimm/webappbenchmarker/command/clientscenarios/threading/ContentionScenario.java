package com.schlimm.webappbenchmarker.command.clientscenarios.threading;

import com.schlimm.webappbenchmarker.client.SimpleRspHandler;
import com.schlimm.webappbenchmarker.client.WBClient;
import com.schlimm.webappbenchmarker.command.ClientCommand;
import com.schlimm.webappbenchmarker.command.Testscenario;
import com.schlimm.webappbenchmarker.protocol.StandardJavaSerialization;

public class ContentionScenario extends Testscenario {

	public ContentionScenario(WBClient client) {
		super(client);
	}

	@Override
	public void execute(Object... args) {
		while (true) { // infinite loop
			SimpleRspHandler rspHandler1 = new SimpleRspHandler();
			SimpleRspHandler rspHandler2 = new SimpleRspHandler();
			client.send(new StandardJavaSerialization().toByteArray(new ClientCommand("com.schlimm.webappbenchmarker.command.threadingissues.Contention")), rspHandler1);
			client.send(new StandardJavaSerialization().toByteArray(new ClientCommand("com.schlimm.webappbenchmarker.command.threadingissues.Contention")), rspHandler2);
			rspHandler1.waitForResponse();
			rspHandler2.waitForResponse();
		}
	}

}
