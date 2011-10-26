package com.schlimm.webappbenchmarker.command.threadingissues.volatiles;

import java.util.Timer;
import java.util.TimerTask;

import com.schlimm.webappbenchmarker.command.ServerCommand;

public class VolatileExample implements ServerCommand {

 public boolean expired;
 private long counter = 0;
 private Object mutex = new Object();

 public Object[] execute(Object... arguments) {
  synchronized (mutex) {
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
  VolatileExample volatileExample = new VolatileExample();
  Thread thread1 = new Thread(volatileExample.new Worker(), "Worker-1");
  thread1.start();
  Thread.currentThread().sleep(60000);
  thread1.interrupt();
 }
}
