package com.schlimm.webappbenchmarker.server;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import com.schlimm.webappbenchmarker.command.ServerCommand;
import com.schlimm.webappbenchmarker.command.system.ServerCommandHandler;
import com.schlimm.webappbenchmarker.protocol.ApplicationLayerProtocol;
import com.schlimm.webappbenchmarker.protocol.StandardJavaSerialization;

public class WorkProcessor implements Runnable {
	
	private BlockingQueue<ServerDataEvent> sharedWorkQueue = new LinkedBlockingQueue<ServerDataEvent>();
	private int poolSize = Runtime.getRuntime().availableProcessors();
	private ExecutorService threadPool = Executors.newFixedThreadPool(poolSize);
	private CompletionService<Object> service = new ExecutorCompletionService<Object>(threadPool);
	private boolean running = true;
	private ServerCommand standardDispatcher = new ServerCommandHandler();

	public void dropData(NioServer server, SocketChannel socket, byte[] data, int count) throws IOException, ClassNotFoundException {
		ApplicationLayerProtocol protocol = new StandardJavaSerialization();
		byte[] dataCopy = new byte[count];
		System.arraycopy(data, 0, dataCopy, 0, count);
		sharedWorkQueue.add(new ServerDataEvent(server, socket, protocol.fromByteArray(dataCopy)));
	}

	public class TaskWorker implements Callable<Object> {

		private ServerDataEvent event;

		public TaskWorker(ServerDataEvent event) {
			super();
			this.event = event;
		}

		@Override
		public Object call() throws Exception {
			event.result = standardDispatcher.execute(event.data);
			return event;
		}
	}

	public class ResultSender implements Runnable {

		private CompletionService<Object> service;
		private boolean running = true;
		private ApplicationLayerProtocol protocol = new StandardJavaSerialization();

		public ResultSender(CompletionService<Object> service) {
			super();
			this.service = service;
		}

		@Override
		public void run() {
			while (this.running) {
				ServerDataEvent event;
				try {
					event = (ServerDataEvent) service.take().get();
				    event.server.send(event.socket, protocol.toByteArray(event.result));
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				} catch (ExecutionException e) {
					e.printStackTrace();
				}
			}
		}

	}

	public void run() {

		Executors.newSingleThreadExecutor().submit(new ResultSender(service));

		while (running) {
			ServerDataEvent event;
			try {
				event = sharedWorkQueue.take();
				service.submit(new TaskWorker(event));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}

	}
}