package com.schlimm.webappbenchmarker.command.trial;

import com.schlimm.webappbenchmarker.command.ServerCommand;

public class SleepCommand implements ServerCommand {

	@Override
	public Object[] execute(Object... arguments) {
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return new Object[]{"Hurra"};
	}

}
