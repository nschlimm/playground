package com.schlimm.webappbenchmarker.command.clientscenarios;

import com.schlimm.webappbenchmarker.client.BenchmarkRspHandler;
import com.schlimm.webappbenchmarker.client.WBClient;
import com.schlimm.webappbenchmarker.command.ClientCommand;
import com.schlimm.webappbenchmarker.command.Testscenario;
import com.schlimm.webappbenchmarker.protocol.ApplicationLayerProtocol;
import com.schlimm.webappbenchmarker.protocol.StandardJavaSerialization;

public class CachePerformanceBenchmark extends Testscenario {

	public CachePerformanceBenchmark(WBClient client) {
		super(client);
	}

	public void execute(Object... args) {
		ApplicationLayerProtocol protocol = new StandardJavaSerialization();
		try {
			System.out.println("Check null - small cache - mixed mode - uninitialized");
			System.out.println(String.format("%1$-20s %2$-15s %3$-11s %4$-10s %5$-10s %6$-10s", "Mean exec count", "Deviation", "JIT before", "JIT after", "CL before", "CL after"));
			for (int i = 0; i < 4; i++) {
				BenchmarkRspHandler handler = new BenchmarkRspHandler();
				client.send(protocol.toByteArray(new ClientCommand("com.schlimm.webappbenchmarker.command.system.BenchmarkCommand", "com.schlimm.webappbenchmarker.command.cachebenchmark.CacheSolution_CheckNull",
						500L, 5, 10, false)), handler);
				handler.waitForResponse();
			}
			System.out.println("Check map - small cache - mixed mode - uninitialized");
			System.out.println(String.format("%1$-20s %2$-15s %3$-11s %4$-10s %5$-10s %6$-10s", "Mean exec count", "Deviation", "JIT before", "JIT after", "CL before", "CL after"));
			for (int i = 0; i < 4; i++) {
				BenchmarkRspHandler handler = new BenchmarkRspHandler();
				client.send(protocol.toByteArray(new ClientCommand("com.schlimm.webappbenchmarker.command.system.BenchmarkCommand", "com.schlimm.webappbenchmarker.command.cachebenchmark.CacheSolution_CheckMap",
						500L, 5, 10, false)), handler);
				handler.waitForResponse();
			}
			System.out.println("putIfAbsent - small cache - mixed mode - uninitialized");
			System.out.println(String.format("%1$-20s %2$-15s %3$-11s %4$-10s %5$-10s %6$-10s", "Mean exec count", "Deviation", "JIT before", "JIT after", "CL before", "CL after"));
			for (int i = 0; i < 4; i++) {
				BenchmarkRspHandler handler = new BenchmarkRspHandler();
				client.send(protocol.toByteArray(new ClientCommand("com.schlimm.webappbenchmarker.command.system.BenchmarkCommand", "com.schlimm.webappbenchmarker.command.cachebenchmark.CacheSolution_PutIfAbsent",
						500L, 5, 10, false)), handler);
				handler.waitForResponse();
			}
			System.out.println("Check null - large cache - mixed mode - uninitialized");
			System.out.println(String.format("%1$-20s %2$-15s %3$-11s %4$-10s %5$-10s %6$-10s", "Mean exec count", "Deviation", "JIT before", "JIT after", "CL before", "CL after"));
			for (int i = 0; i < 4; i++) {
				BenchmarkRspHandler handler = new BenchmarkRspHandler();
				client.send(protocol.toByteArray(new ClientCommand("com.schlimm.webappbenchmarker.command.system.BenchmarkCommand", "com.schlimm.webappbenchmarker.command.cachebenchmark.CacheSolution_CheckNull",
						500L, 5, 100000, false)), handler);
				handler.waitForResponse();
			}
			System.out.println("Check map - large cache - mixed mode - uninitialized");
			System.out.println(String.format("%1$-20s %2$-15s %3$-11s %4$-10s %5$-10s %6$-10s", "Mean exec count", "Deviation", "JIT before", "JIT after", "CL before", "CL after"));
			for (int i = 0; i < 4; i++) {
				BenchmarkRspHandler handler = new BenchmarkRspHandler();
				client.send(protocol.toByteArray(new ClientCommand("com.schlimm.webappbenchmarker.command.system.BenchmarkCommand", "com.schlimm.webappbenchmarker.command.cachebenchmark.CacheSolution_CheckMap",
						500L, 5, 100000, false)), handler);
				handler.waitForResponse();
			}
			System.out.println("putIfAbsent - large cache - mixed mode - uninitialized");
			System.out.println(String.format("%1$-20s %2$-15s %3$-11s %4$-10s %5$-10s %6$-10s", "Mean exec count", "Deviation", "JIT before", "JIT after", "CL before", "CL after"));
			for (int i = 0; i < 4; i++) {
				BenchmarkRspHandler handler = new BenchmarkRspHandler();
				client.send(protocol.toByteArray(new ClientCommand("com.schlimm.webappbenchmarker.command.system.BenchmarkCommand", "com.schlimm.webappbenchmarker.command.cachebenchmark.CacheSolution_PutIfAbsent",
						500L, 5, 100000, false)), handler);
				handler.waitForResponse();
			}
			System.out.println("Check null - very large cache - mixed mode - uninitialized");
			System.out.println(String.format("%1$-20s %2$-15s %3$-11s %4$-10s %5$-10s %6$-10s", "Mean exec count", "Deviation", "JIT before", "JIT after", "CL before", "CL after"));
			for (int i = 0; i < 4; i++) {
				BenchmarkRspHandler handler = new BenchmarkRspHandler();
				client.send(protocol.toByteArray(new ClientCommand("com.schlimm.webappbenchmarker.command.system.BenchmarkCommand", "com.schlimm.webappbenchmarker.command.cachebenchmark.CacheSolution_CheckNull",
						500L, 5, 1000000, false)), handler);
				handler.waitForResponse();
			}
			System.out.println("Check map - very large cache - mixed mode - uninitialized");
			System.out.println(String.format("%1$-20s %2$-15s %3$-11s %4$-10s %5$-10s %6$-10s", "Mean exec count", "Deviation", "JIT before", "JIT after", "CL before", "CL after"));
			for (int i = 0; i < 4; i++) {
				BenchmarkRspHandler handler = new BenchmarkRspHandler();
				client.send(protocol.toByteArray(new ClientCommand("com.schlimm.webappbenchmarker.command.system.BenchmarkCommand", "com.schlimm.webappbenchmarker.command.cachebenchmark.CacheSolution_CheckMap",
						500L, 5, 1000000, false)), handler);
				handler.waitForResponse();
			}
			System.out.println("putIfAbsent - very large cache - mixed mode - uninitialized");
			System.out.println(String.format("%1$-20s %2$-15s %3$-11s %4$-10s %5$-10s %6$-10s", "Mean exec count", "Deviation", "JIT before", "JIT after", "CL before", "CL after"));
			for (int i = 0; i < 4; i++) {
				BenchmarkRspHandler handler = new BenchmarkRspHandler();
				client.send(protocol.toByteArray(new ClientCommand("com.schlimm.webappbenchmarker.command.system.BenchmarkCommand", "com.schlimm.webappbenchmarker.command.cachebenchmark.CacheSolution_PutIfAbsent",
						500L, 5, 1000000, false)), handler);
				handler.waitForResponse();
			}
			System.out.println("Check null - small cache - mixed mode - initialized");
			System.out.println(String.format("%1$-20s %2$-15s %3$-11s %4$-10s %5$-10s %6$-10s", "Mean exec count", "Deviation", "JIT before", "JIT after", "CL before", "CL after"));
			for (int i = 0; i < 4; i++) {
				BenchmarkRspHandler handler = new BenchmarkRspHandler();
				client.send(protocol.toByteArray(new ClientCommand("com.schlimm.webappbenchmarker.command.system.BenchmarkCommand", "com.schlimm.webappbenchmarker.command.cachebenchmark.CacheSolution_CheckNull",
						500L, 5, 10, true)), handler);
				handler.waitForResponse();
			}
			System.out.println("Check map - small cache - mixed mode - initialized");
			System.out.println(String.format("%1$-20s %2$-15s %3$-11s %4$-10s %5$-10s %6$-10s", "Mean exec count", "Deviation", "JIT before", "JIT after", "CL before", "CL after"));
			for (int i = 0; i < 4; i++) {
				BenchmarkRspHandler handler = new BenchmarkRspHandler();
				client.send(protocol.toByteArray(new ClientCommand("com.schlimm.webappbenchmarker.command.system.BenchmarkCommand", "com.schlimm.webappbenchmarker.command.cachebenchmark.CacheSolution_CheckMap",
						500L, 5, 10, true)), handler);
				handler.waitForResponse();
			}
			System.out.println("putIfAbsent - small cache - mixed mode - initialized");
			System.out.println(String.format("%1$-20s %2$-15s %3$-11s %4$-10s %5$-10s %6$-10s", "Mean exec count", "Deviation", "JIT before", "JIT after", "CL before", "CL after"));
			for (int i = 0; i < 4; i++) {
				BenchmarkRspHandler handler = new BenchmarkRspHandler();
				client.send(protocol.toByteArray(new ClientCommand("com.schlimm.webappbenchmarker.command.system.BenchmarkCommand", "com.schlimm.webappbenchmarker.command.cachebenchmark.CacheSolution_PutIfAbsent",
						500L, 5, 10, true)), handler);
				handler.waitForResponse();
			}
			System.out.println("Check null - large cache - mixed mode - initialized");
			System.out.println(String.format("%1$-20s %2$-15s %3$-11s %4$-10s %5$-10s %6$-10s", "Mean exec count", "Deviation", "JIT before", "JIT after", "CL before", "CL after"));
			for (int i = 0; i < 4; i++) {
				BenchmarkRspHandler handler = new BenchmarkRspHandler();
				client.send(protocol.toByteArray(new ClientCommand("com.schlimm.webappbenchmarker.command.system.BenchmarkCommand", "com.schlimm.webappbenchmarker.command.cachebenchmark.CacheSolution_CheckNull",
						500L, 5, 100000, true)), handler);
				handler.waitForResponse();
			}
			System.out.println("Check map - large cache - mixed mode - initialized");
			System.out.println(String.format("%1$-20s %2$-15s %3$-11s %4$-10s %5$-10s %6$-10s", "Mean exec count", "Deviation", "JIT before", "JIT after", "CL before", "CL after"));
			for (int i = 0; i < 4; i++) {
				BenchmarkRspHandler handler = new BenchmarkRspHandler();
				client.send(protocol.toByteArray(new ClientCommand("com.schlimm.webappbenchmarker.command.system.BenchmarkCommand", "com.schlimm.webappbenchmarker.command.cachebenchmark.CacheSolution_CheckMap",
						500L, 5, 100000, true)), handler);
				handler.waitForResponse();
			}
			System.out.println("putIfAbsent - large cache - mixed mode - initialized");
			System.out.println(String.format("%1$-20s %2$-15s %3$-11s %4$-10s %5$-10s %6$-10s", "Mean exec count", "Deviation", "JIT before", "JIT after", "CL before", "CL after"));
			for (int i = 0; i < 4; i++) {
				BenchmarkRspHandler handler = new BenchmarkRspHandler();
				client.send(protocol.toByteArray(new ClientCommand("com.schlimm.webappbenchmarker.command.system.BenchmarkCommand", "com.schlimm.webappbenchmarker.command.cachebenchmark.CacheSolution_PutIfAbsent",
						500L, 5, 100000, true)), handler);
				handler.waitForResponse();
			}
			System.out.println("Check null - very large cache - mixed mode - initialized");
			System.out.println(String.format("%1$-20s %2$-15s %3$-11s %4$-10s %5$-10s %6$-10s", "Mean exec count", "Deviation", "JIT before", "JIT after", "CL before", "CL after"));
			for (int i = 0; i < 4; i++) {
				BenchmarkRspHandler handler = new BenchmarkRspHandler();
				client.send(protocol.toByteArray(new ClientCommand("com.schlimm.webappbenchmarker.command.system.BenchmarkCommand", "com.schlimm.webappbenchmarker.command.cachebenchmark.CacheSolution_CheckNull",
						500L, 5, 1000000, true)), handler);
				handler.waitForResponse();
			}
			System.out.println("Check map - very large cache - mixed mode - initialized");
			System.out.println(String.format("%1$-20s %2$-15s %3$-11s %4$-10s %5$-10s %6$-10s", "Mean exec count", "Deviation", "JIT before", "JIT after", "CL before", "CL after"));
			for (int i = 0; i < 4; i++) {
				BenchmarkRspHandler handler = new BenchmarkRspHandler();
				client.send(protocol.toByteArray(new ClientCommand("com.schlimm.webappbenchmarker.command.system.BenchmarkCommand", "com.schlimm.webappbenchmarker.command.cachebenchmark.CacheSolution_CheckMap",
						500L, 5, 1000000, true)), handler);
				handler.waitForResponse();
			}
			System.out.println("putIfAbsent - very large cache - mixed mode - initialized");
			System.out.println(String.format("%1$-20s %2$-15s %3$-11s %4$-10s %5$-10s %6$-10s", "Mean exec count", "Deviation", "JIT before", "JIT after", "CL before", "CL after"));
			for (int i = 0; i < 4; i++) {
				BenchmarkRspHandler handler = new BenchmarkRspHandler();
				client.send(protocol.toByteArray(new ClientCommand("com.schlimm.webappbenchmarker.command.system.BenchmarkCommand", "com.schlimm.webappbenchmarker.command.cachebenchmark.CacheSolution_PutIfAbsent",
						500L, 5, 1000000, true)), handler);
				handler.waitForResponse();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
