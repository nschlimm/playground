package com.schlimm.java7.benchmark.addon;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.List;

@SuppressWarnings("unused")
public class SystemInformation {

	private ClassLoadingMXBean classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();
	private CompilationMXBean compilationMXBean = ManagementFactory.getCompilationMXBean();
	private List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
	private MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
	private OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
	private RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
	private ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

	public void printThreadInfo(boolean includeThreadNames) {
		System.out.println("Total started thread count: " + getThreadMXBean().getTotalStartedThreadCount());
		System.out.println("Peak thread count: " + getThreadMXBean().getPeakThreadCount());
		System.out.println("Deamon thread count: " + getThreadMXBean().getDaemonThreadCount());
		System.out.println("Thread count: " + getThreadMXBean().getThreadCount());
		StringBuffer namelist = new StringBuffer();
		if (includeThreadNames) {
			for (ThreadInfo threadInfo : getThreadMXBean().getThreadInfo(getThreadMXBean().getAllThreadIds())) {
				namelist.append(threadInfo.getThreadName()).append("...");
			}
			System.out.println("Thread names: " + namelist);
		}
	}

	public ThreadMXBean getThreadMXBean() {
		return threadMXBean;
	}

	public CompilationMXBean getCompilationMXBean() {
		return compilationMXBean;
	}

	public MemoryMXBean getMemoryMXBean() {
		return memoryMXBean;
	}

	public RuntimeMXBean getRuntimeMXBean() {
		return runtimeMXBean;
	}

	public List<GarbageCollectorMXBean> getGarbageCollectorMXBeans() {
		return garbageCollectorMXBeans;
	}
	
	public long getCollectionTime() {
		long time = 0;
		for (GarbageCollectorMXBean garbageCollectorMXBean : garbageCollectorMXBeans) {
			time += garbageCollectorMXBean.getCollectionTime();
		}
		return time;
	}

}
