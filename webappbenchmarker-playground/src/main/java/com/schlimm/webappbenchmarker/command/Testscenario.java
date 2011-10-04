package com.schlimm.webappbenchmarker.command;

import com.schlimm.webappbenchmarker.client.WBClient;

public abstract class Testscenario {
	
	protected WBClient client = null;
	
	public Testscenario(WBClient client) {
		super();
		this.client = client;
	}

	public abstract void execute(Object... args);

}
