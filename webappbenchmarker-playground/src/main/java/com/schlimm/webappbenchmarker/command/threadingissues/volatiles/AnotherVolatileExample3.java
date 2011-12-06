package com.schlimm.webappbenchmarker.command.threadingissues.volatiles;

import java.util.Timer;
import java.util.TimerTask;

import com.schlimm.webappbenchmarker.command.ServerCommand;

/**
 * Synchronized read of expired status
 * 
 * Results: option -server
 * status		expired			result
 * non-volatile non-volatile	positive
 * 
 * @author Niklas Schlimm
 *
 */
public class AnotherVolatileExample3 implements ServerCommand {

 private WorkStatus status = new WorkStatus(); // non volatile status does not work
 private long counter = 0;
 private Object mutext = new Object();

 public Object[] execute(Object... arguments) {
   status.expired = false;
   final Timer timer = new Timer();
   timer.schedule(new TimerTask() {
    public void run() {
     status.expired = true;
     System.out.println("Timer interrupted main thread ...");
     timer.cancel();
    }
   }, 1000);
   boolean expired = false;
   while (!expired) {
    counter++; // do some work
    synchronized (mutext) {
		expired = status.expired;
	}
   }
   System.out.println("Main thread was interrupted by timer ...");
  return new Object[] { counter, status };
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
  AnotherVolatileExample3 volatileExample = new AnotherVolatileExample3();
  Thread thread1 = new Thread(volatileExample.new Worker(), "Worker-1");
  thread1.start();
  Thread.currentThread().sleep(60000);
  thread1.interrupt();
 }
 
 private class WorkStatus {
	 public boolean expired = false;
 }
}
