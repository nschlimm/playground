package com.schlimm.java7.nio.investigation.concurrency;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutionException;

public class LockProtocol {

	
	public static void main(String[] args) throws IOException {

		// exercise lock methods
		AsynchronousFileChannel ch = AsynchronousFileChannel.open(Paths.get("e:/temp/aFile.out"), READ, WRITE,
				StandardOpenOption.CREATE);
		FileLock fl;
		try {
			// test 1 - acquire lock and check that tryLock throws
			// OverlappingFileLockException
			try {
				fl = ch.lock().get();
			} catch (ExecutionException x) {
				throw new RuntimeException(x);
			} catch (InterruptedException x) {
				throw new RuntimeException("Should not be interrupted");
			}
			if (!fl.acquiredBy().equals(ch))
				throw new RuntimeException("FileLock#acquiredBy returned incorrect channel");
			try {
				ch.tryLock();
				throw new RuntimeException("OverlappingFileLockException expected");
			} catch (OverlappingFileLockException x) {
			}
			fl.release();

			// test 2 - acquire try and check that lock throws OverlappingFileLockException
			fl = ch.tryLock();
			if (fl == null)
				throw new RuntimeException("Unable to acquire lock");
			try {
				ch.lock((Void) null, new CompletionHandler<FileLock, Void>() {
					public void completed(FileLock result, Void att) {
					}

					public void failed(Throwable exc, Void att) {
					}
				});
				throw new RuntimeException("OverlappingFileLockException expected");
			} catch (OverlappingFileLockException x) {
			}
		} finally {
			ch.close();
		}

		// test 3 - channel is closed so FileLock should no longer be valid
		if (fl.isValid())
			throw new RuntimeException("FileLock expected to be invalid");

	}
}
