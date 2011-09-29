package com.schlimm.webappbenchmarker.command.std;

import com.schlimm.webappbenchmarker.command.ServerCommand;

public class CommandDummy implements ServerCommand{

	public CommandDummy() {
		super();
	}

	@Override
	public Object[] execute(Object... arguments) {
		return null;
	}

}
