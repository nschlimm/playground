package com.schlimm.webappbenchmarker.command.threadingissues.volatiles;

import java.util.Timer;
import java.util.TimerTask;

import com.schlimm.webappbenchmarker.command.ServerCommand;

/**
 * WorkStatus-Object without accessors.
 * 
 * Results: option -server
 * status		expired			result
 * non-volatile non-volatile	negative
 * volatile		non-volatile	positive
 * non-volatile	volatile		positive
 * 
 * @author Niklas Schlimm
 *
 */
public class AnotherVolatileExample2 implements ServerCommand {

 private WorkStatus status = new WorkStatus();
 private long counter = 0;

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
   while (!status.expired) {
    counter++; // do some work
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
  AnotherVolatileExample2 volatileExample = new AnotherVolatileExample2();
  Thread thread1 = new Thread(volatileExample.new Worker(), "Worker-1");
  thread1.start();
  Thread.currentThread().sleep(60000);
  thread1.interrupt();
 }
 
 private class WorkStatus {
	 public volatile boolean expired = false;
 }
}
