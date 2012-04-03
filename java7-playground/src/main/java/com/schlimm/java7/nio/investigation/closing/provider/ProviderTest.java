package com.schlimm.java7.nio.investigation.closing.provider;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class ProviderTest {

	public static void main(String[] args) throws IOException, URISyntaxException {
		AsynchronousFileChannel channel = AsynchronousFileChannel.open(Paths.get(new URI("safe:/E:/temp/afile.out")),
				StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		channel.write(ByteBuffer.wrap("Hello".getBytes()), 0L);
	}
}
