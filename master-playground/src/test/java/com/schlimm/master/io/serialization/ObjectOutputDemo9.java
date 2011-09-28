package com.schlimm.master.io.serialization;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class ObjectOutputDemo9 {
	public static void main(String argv[]) throws Exception {
		new Server();
		ObjectOutputStream oos = new ObjectOutputStream(new Socket("localhost", 3000).getOutputStream());		
		try {
			while (true) {
				oos.writeObject(new byte[10240]);
			}
		} finally {
			oos.close();
		}
	}
}

class Server extends Thread {

	private ServerSocket server;

	public Server() throws Exception {
		server = new ServerSocket(3000);
		System.out.println("Listening on port 3000.");
		this.start();
	}

	public void run() {
		while (true) {
			try {
				System.out.println("Server waiting for client connections ...");
				Socket client = server.accept();
				System.out.println("Accepted a new connection from client: " + client.getInetAddress());
				@SuppressWarnings("unused")
				Connection c = new Connection(client);
			} catch (Exception e) {
			}
		}
	}
}

class Connection extends Thread {
	private Socket client = null;
	private ObjectInputStream ois = null;

	public Connection() {
	}

	public Connection(Socket clientSocket) {
		client = clientSocket;
		try {
			ois = new ObjectInputStream(client.getInputStream());
		} catch (Exception e1) {
			try {
				client.close();
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
			return;
		}
		this.start();
	}

	public void run() {
		try {
			Object obj;
			while ((obj = ois.readObject()) != null) {
				System.out.println(obj);
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		} finally {
			try {
				ois.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
