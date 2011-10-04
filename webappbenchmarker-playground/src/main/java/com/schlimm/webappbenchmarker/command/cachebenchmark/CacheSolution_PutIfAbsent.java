package com.schlimm.webappbenchmarker.command.cachebenchmark;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.schlimm.webappbenchmarker.command.ServerCommand;

public class CacheSolution_PutIfAbsent implements Runnable, ServerCommand {

	private ConcurrentHashMap<String, SomeCPUIntenseTask> commandCache = new ConcurrentHashMap<String, SomeCPUIntenseTask>();
	private Lock lock = new ReentrantLock();
	private int cacheSize = 10;
	String[] keys;

	public CacheSolution_PutIfAbsent(Integer cacheSize) {
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
		SomeCPUIntenseTask newServerCommand;
		SomeCPUIntenseTask serverCommand = commandCache.putIfAbsent(clientCommand, newServerCommand = new SomeCPUIntenseTask());
		if (serverCommand == null) {
			serverCommand = newServerCommand;
		}
		return new Object[]{serverCommand.doCPUIntenseTask()};
	}

	public class SomeCPUIntenseTask {

		private Object taskResult = null;

		public Object doCPUIntenseTask() {
			if (taskResult != null)
				return taskResult; // avoid locking overhead
			else {
				try {
					lock.lock();
					if (taskResult != null) // two threads may have been in race condition
						return taskResult;
					// perform CPU intense task
					taskResult = new Object();
					return taskResult;
				} finally {
					lock.unlock();
				}
			}

		}

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
