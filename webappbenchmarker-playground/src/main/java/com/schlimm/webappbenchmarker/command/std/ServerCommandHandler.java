package com.schlimm.webappbenchmarker.command.std;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.schlimm.webappbenchmarker.command.ClientCommand;
import com.schlimm.webappbenchmarker.command.ServerCommand;

public class ServerCommandHandler implements ServerCommand {

	private Map<String, ServerCommand> commandCache = new ConcurrentHashMap<String, ServerCommand>();
	private Lock lock = new ReentrantLock();
	
	@Override
	public Object[] execute(Object... arguments) {
		ClientCommand clientCommand = (ClientCommand)arguments[0];
		ServerCommand serverCommand = commandCache.get(clientCommand.getServerCommandClassName());
		if (serverCommand==null) {
			lock.lock();
			try {
				if (commandCache.containsKey(clientCommand.getServerCommandClassName())) 
					serverCommand = commandCache.get(clientCommand.getServerCommandClassName());
				else {
				    try {
						serverCommand = (ServerCommand) Class.forName(clientCommand.getServerCommandClassName()).getConstructor(new Class[]{}).newInstance();
					} catch (ClassNotFoundException e) {
						System.out.println("Could not find class: " + clientCommand.getServerCommandClassName());
					} catch (Exception e) {
						e.printStackTrace();
					}
				    commandCache.put(clientCommand.getServerCommandClassName(), serverCommand);
				}
			} finally {
				lock.unlock();
			}
		}
		System.out.println(ManagementFactory.getCompilationMXBean().getTotalCompilationTime());
		Object[] result = serverCommand.execute(clientCommand.getArguments());
		System.out.println(ManagementFactory.getCompilationMXBean().getTotalCompilationTime());
		return result;
	}

}
