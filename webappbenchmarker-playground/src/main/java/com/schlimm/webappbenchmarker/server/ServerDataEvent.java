package com.schlimm.webappbenchmarker.server;

import java.nio.channels.SocketChannel;

class ServerDataEvent {
	public NioServer server;
	public SocketChannel socket;
	public Object data;
	public Object result;
	
	public ServerDataEvent(NioServer server, SocketChannel socket, Object data) {
		this.server = server;
		this.socket = socket;
		this.data = data;
	}
	
}