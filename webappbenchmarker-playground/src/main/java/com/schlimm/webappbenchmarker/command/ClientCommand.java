package com.schlimm.webappbenchmarker.command;

import java.io.Serializable;

public class ClientCommand implements Serializable {
	
	/**
	 * Version
	 */
	private static final long serialVersionUID = 1L;
	
	private String serverCommandClassName;
	private Object[] arguments;

	public ClientCommand(String serverCommandClassName, Object... arguments) {
		super();
		this.serverCommandClassName = serverCommandClassName;
		this.arguments = arguments;
	}

	public String getServerCommandClassName() {
		return serverCommandClassName;
	}

	public Object[] getArguments() {
		return arguments;
	}
	
}
