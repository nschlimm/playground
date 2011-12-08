package com.schlimm.java7.concurrency.phaser;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Phaser;

public class PhaserExample {

	void runTasks(List<Runnable> tasks) throws InterruptedException {

		/** The phaser is a nice synchronization barrier. */
		final Phaser phaser = new Phaser(1) {
			/**
			 * onAdvance() is invoked when all threads reached the synchronization barrier. It returns true if the
			 * phaser should terminate, false if phaser should continue with next phase When terminated: (1) attempts to
			 * register upon termination have no effect and (2) synchronization methods immediately return without
			 * waiting for advance. When continue:
			 * <p>
			 * 
			 * <pre>
			 *       -> set unarrived parties = registered parties
			 *       -> set arrived parties = 0
			 *       -> set phase = phase + 1
			 * </pre>
			 */
			protected boolean onAdvance(int phase, int registeredParties) {
				System.out.println("On advance" + " -> Registered: " + getRegisteredParties() + " - Unarrived: "
						+ getUnarrivedParties() + " - Arrived: " + getArrivedParties() + " - Phase: " + getPhase());
				/**
				 * this onAdvance() implementation causes the phaser to cycle 10 times
				 */
				return phase >= 1 || registeredParties == 0;
			}
		};

		dumpPhaserState("After phaser init", phaser);

		/** Create and start threads */
		for (final Runnable task : tasks) {
			/**
			 * Increase the number of unarrived parties -> equals the number of parties required to advance to the next
			 * phase.
			 */
			phaser.register();
			dumpPhaserState("After register", phaser);
			new Thread() {
				public void run() {
					do {
						/**
						 * Wait for all threads reaching the synchronization barrier: wait for arrived parties =
						 * registered parties. If arrived parties = registered parties: phase advances and onAdvance()
						 * is invoked.
						 */
						phaser.arriveAndAwaitAdvance();
						task.run();
					} while (!phaser.isTerminated());
				}
			}.start();
			Thread.sleep(500);
			dumpPhaserState("After arrival", phaser);
		}

		/**
		 * When the final party for a given phase arrives, onAdvance() is invoked and the phase advances. The
		 * "face advances" means that all threads reached the barrier and therefore all threads are synchronized and can
		 * continue processing.
		 */
		dumpPhaserState("Before main thread arrives and deregisters", phaser);
		/**
		 * The arrival and deregistration of the main thread allows the other threads to start working. This is because
		 * now the registered parties equal the arrived parties.
		 */
		phaser.arriveAndDeregister();
		dumpPhaserState("After main thread arrived and deregistered", phaser);
		System.out.println("Main thread will terminate ...");
	}

	private void dumpPhaserState(String when, Phaser phaser) {
		System.out.println(when + " -> Registered: " + phaser.getRegisteredParties() + " - Unarrived: "
				+ phaser.getUnarrivedParties() + " - Arrived: " + phaser.getArrivedParties() + " - Phase: "
				+ phaser.getPhase());
	}

	public static void main(String[] args) throws InterruptedException {

		List<Runnable> tasks = new ArrayList<>();

		for (int i = 0; i < 2; i++) {

			Runnable runnable = new Runnable() {
				@Override
				public void run() {
					System.out.println(Thread.currentThread().getName() + ":go  :" + new Date());
					int a = 0, b = 1;
					for (int i = 0; i < 2000000000; i++) {
						a = a + b;
						b = a - b;
					}
					System.out.println(Thread.currentThread().getName() + ":done:" + new Date());
				}
			};

			tasks.add(runnable);

		}

		new PhaserExample().runTasks(tasks);

	}
}
