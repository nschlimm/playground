package com.schlimm.webappbenchmarker.command.threadingissues;

import java.util.Timer;
import java.util.TimerTask;

import com.schlimm.webappbenchmarker.command.ServerCommand;

public class ContentionSynchronized implements ServerCommand {

	private boolean expired;
	private long counter = 0;
	private Object mutext = new Object();

	@Override
	public Object[] execute(Object... arguments) {
		synchronized (mutext) {
			expired = false;
			final Timer timer = new Timer();
			timer.schedule(new TimerTask() {
				public void run() {
					expired = true;
					System.out.println("Timer interrupted main thread ...");
					timer.cancel();
				}
			}, 1000);
			while (!expired) {
				counter++; // do some work
			}
			System.out.println("Main thread was interrupted by timer ...");
		};
		return new Object[] { counter, expired };
	}

	private class Worker implements Runnable {
		@Override
		public void run() {
			while (!Thread.currentThread().isInterrupted()) {
				execute();
			}
		}
	}

	@SuppressWarnings("static-access")
	public static void main(String[] args) throws InterruptedException {
		ContentionSynchronized contentionSynchronized = new ContentionSynchronized();
		Thread thread1 = new Thread(contentionSynchronized.new Worker(), "Worker-1");
		Thread thread2 = new Thread(contentionSynchronized.new Worker(), "Worker-2");
		thread1.start();
		thread2.start();
		Thread.currentThread().sleep(60000);
		thread1.interrupt();
		thread2.interrupt();
	}
	
//	private Socket openSocket(String server, int port) throws Exception
//	  {
//	    Socket socket;
//	    
//	    // create a socket with a timeout
//	    try
//	    {
//	      InetAddress inteAddress = InetAddress.getByName(server);
//	      SocketAddress socketAddress = new InetSocketAddress(inteAddress, port);
//	  
//	      // create a socket
//	      socket = new Socket();
//	  
//	      // this method will block no more than timeout ms.
//	      int timeoutInMs = 10*1000;   // 10 seconds
//	      socket.connect(socketAddress, timeoutInMs);
//	      
//	      return socket;
//	    } 
//	    catch (SocketTimeoutException ste) 
//	    {
//	      System.err.println("Timed out waiting for the socket.");
//	      ste.printStackTrace();
//	      throw ste;
//	    }
//	  }


}
