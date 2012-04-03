package com.schlimm.java7.nio.investigation.closing.provider;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class ProviderTest {

	public static void main(String[] args) throws IOException, URISyntaxException {
		AsynchronousFileChannel channel = AsynchronousFileChannel.open(Paths.get(new URI("safe:/E:/temp/afile.out")),
				StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		for (int i = 0; i < 10000; i++) {
			channel.write(ByteBuffer.wrap("Hello".getBytes()), 0L);
		}
		channel.close();
		long size = Files.size(Paths.get(new URI("safe:/E:/temp/afile.out")));
		System.out.println(size);
	}
}
