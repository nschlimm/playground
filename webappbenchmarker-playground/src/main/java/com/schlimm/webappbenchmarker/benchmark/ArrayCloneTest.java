package com.schlimm.webappbenchmarker.benchmark;

import java.util.Random;

public class ArrayCloneTest implements Runnable {
  private final byte[] byteValue;

  public ArrayCloneTest(Integer length) {
    byteValue = new byte[length];
    // always the same set of bytes...
    new Random(0).nextBytes(byteValue);
  }

  public void run() {
    byte[] result = byteValue.clone();
  }
}