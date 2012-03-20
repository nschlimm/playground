package com.schlimm.java7.nio.threadpools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

public class AsynchronousTask implements Runnable {

	private static final String IO = "IO";
	private static final String COMPUTE = "COMPUTE";
	private static final String SLEEP = "SLEEP";
	private static final String IO2 = "IO2";
	private static final String AFILE_OUT = "afile.out";
	private int td;
	private String type;
	public int result;
	private int id;

	public AsynchronousTask(int id, String type, int td) {
		super();
		this.td = td;
		this.type = type;
		this.id = id;
	}

	@Override
	public void run() {
		switch (type) {
		case SLEEP:
			sleep();
			break;

		case COMPUTE:
			compute();
			break;

		case IO:
			write();
			break;

		case IO2:
			write2();
			break;

		default:
			throw new IllegalArgumentException("Unknown task type! " + type);
		}
	}

	private void write() {
		try (FileOutputStream fileos = new FileOutputStream(new File(AFILE_OUT), true)) {
			fileos.write(String.format("%1$-" + td + "s", "s").getBytes());
		} catch (NumberFormatException | IOException e) {
			e.printStackTrace();
		}
	}

	private void write2() {
		try (FileOutputStream fileos = new FileOutputStream(new File(String.valueOf(id)), true)) {
			fileos.write(String.format("%1$-" + td + "s", "s").getBytes());
		} catch (NumberFormatException | IOException e) {
			e.printStackTrace();
		} finally {
			new File(String.valueOf(id)).delete();
		}
	}

	private void compute() {
		for (int i = 1; i <= td; i++)
			result += ThreadLocalRandom.current().nextInt();
	}

	private void sleep() {
		try {
			Thread.sleep(td);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}
