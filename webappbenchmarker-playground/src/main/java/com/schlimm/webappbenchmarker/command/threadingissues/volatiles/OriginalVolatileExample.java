package com.schlimm.webappbenchmarker.command.threadingissues.volatiles;

import java.util.Timer;
import java.util.TimerTask;

public class OriginalVolatileExample {

 private boolean expired;
 private long counter = 0;
 private Object mutext = new Object();

 public Object[] execute(Object... arguments) {
   expired = false;
   final Timer timer = new Timer();
   timer.schedule(new TimerTask() {
	   public void run() {
		    synchronized (mutext) {
		        expired = true;
		    }
		    System.out.println("Timer interrupted main thread ...");
		    timer.cancel();
		}   }, 1000);
   while (!expired) {
    counter++; // do some work
   }
   System.out.println("Main thread was interrupted by timer ...");
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
  OriginalVolatileExample volatileExample = new OriginalVolatileExample();
  Thread thread1 = new Thread(volatileExample.new Worker(), "Worker-1");
  Thread thread2 = new Thread(volatileExample.new Worker(), "Worker-2");
  thread1.start();
  thread2.start();
  Thread.currentThread().sleep(60000);
  thread1.interrupt();
  thread2.interrupt();
 }
}