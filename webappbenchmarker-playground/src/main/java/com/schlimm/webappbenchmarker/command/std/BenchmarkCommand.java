package com.schlimm.webappbenchmarker.command.std;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.schlimm.webappbenchmarker.command.ServerCommand;
import com.schlimm.webappbenchmarker.statistic.Average;
import com.schlimm.webappbenchmarker.statistic.PerformanceChecker;
import com.schlimm.webappbenchmarker.statistic.PerformanceHarness;

public class BenchmarkCommand implements ServerCommand {

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Object[] execute(Object... arguments) {
		String classname = (String) arguments[0];
		long testtime = (Long) arguments[1];
		int testruns = (Integer) arguments[2];
		Object[] benchmarkConstructorArguments = Arrays.copyOfRange(arguments, 3, arguments.length);
		List<Class> constructorArgumentTypeList = new ArrayList<Class>();
		for (Object object : benchmarkConstructorArguments) {
			constructorArgumentTypeList.add(object.getClass());
		}
		Class[] constrArgTypeArray = constructorArgumentTypeList.toArray(new Class[constructorArgumentTypeList.size()]);
		Class benchmarkClass = null; Runnable benachmarkObject = null;
		try {
			benchmarkClass = Class.forName(classname);
			benachmarkObject = (Runnable) benchmarkClass.getConstructor(constrArgTypeArray).newInstance(benchmarkConstructorArguments);
		} catch (Exception e) {
			e.printStackTrace();
		}
		PerformanceHarness harness = new PerformanceHarness();
	    Average arrayClone = harness.calculatePerf(
	            new PerformanceChecker(testtime,
	                benachmarkObject), testruns);
		return new Object[]{arrayClone};
	}

}
