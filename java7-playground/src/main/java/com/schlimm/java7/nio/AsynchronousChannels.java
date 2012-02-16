package com.schlimm.java7.nio;

import java.io.IOException;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.Future;

public class AsynchronousChannels {
	
	public static void main(String[] args) throws IOException {
		AsynchronousServerSocketChannel server =
			    AsynchronousServerSocketChannel.open().bind(null);
		Future<AsynchronousSocketChannel> acceptFuture = server.accept();
	}

}
