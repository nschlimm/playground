package com.schlimm.master.io.nio;

import java.nio.ByteBuffer;

public class Buffers {

	public static void main(String[] args) {
		System.out.println("... writing 'hello' to buffer ...");
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		byte[] hello = "Hello".getBytes();
		buffer.put(hello); // writing
		System.out.println("Capacity: " + buffer.capacity());
		System.out.println("Limit: " + buffer.limit());
		System.out.println("Position: " + buffer.position());
		buffer.flip(); // set limit to current position and position back to beginning -> ready to read
		System.out.println("... flipped ...");
		System.out.println("Capacity: " + buffer.capacity());
		System.out.println("Limit: " + buffer.limit());
		System.out.println("Position: " + buffer.position());
		while (buffer.hasRemaining()) {
			System.out.println((char)buffer.get());
		}
		System.out.println("... converting to double ...");
		ByteBuffer anotherBuffer = ByteBuffer.allocate(1024);
		byte[] heinz = "8Bytes--".getBytes();
		anotherBuffer.put(heinz);
		anotherBuffer.flip();
		System.out.println(anotherBuffer.getDouble());
	}
}
