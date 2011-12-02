package com.schlimm.webappbenchmarker.command.threadingissues.contention;

public class SomeClientThread implements Runnable {

	private SomeResource resource;
	
	public SomeClientThread(SomeResource resource) {
		super();
		this.resource = resource;
	}

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			resource.access("arbitraryArgument");
		}
	}

}
