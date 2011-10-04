package com.schlimm.webappbenchmarker.command.cachebenchmark;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.schlimm.webappbenchmarker.command.ServerCommand;

public class CacheSolution_CheckNull implements Runnable, ServerCommand {

	private Map<String, String> commandCache = new ConcurrentHashMap<String, String>();
	private Lock lock = new ReentrantLock();
	private int cacheSize = 10;
	String[] keys;

	public CacheSolution_CheckNull(Integer cacheSize) {
		super();
		this.cacheSize = cacheSize;
		keys = new String[cacheSize];
		for (int i = 0; i < cacheSize; i++) {
			keys[i] = Long.toHexString(Double.doubleToLongBits(Math.random()));
		}
	}

	@Override
	public Object[] execute(Object... arguments) {
		String clientCommand = (String) arguments[0];
		String serverCommand = commandCache.get(clientCommand);
		if (serverCommand == null) {
			lock.lock();
			try {
				if (commandCache.containsKey(clientCommand))
					serverCommand = commandCache.get(clientCommand);
				else {
					// do something CPU intensive (is not relevant for test result here)
					serverCommand = "dummy string";
					commandCache.put(clientCommand, serverCommand);
				}
			} finally {
				lock.unlock();
			}
		}
		Object[] result = new Object[] { serverCommand };
		return result;
	}

	@Override
	public void run() {
		execute(keys[new Random().nextInt(cacheSize)]);
	}

	@Override
	public String toString() {
		return "class - " + this.getClass() + " - cache size : " + cacheSize;
	}

}
