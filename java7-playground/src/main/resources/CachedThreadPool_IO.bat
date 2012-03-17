ECHO OFF
SET CLASSPATH=D:/dev_home/repositories/git/playground/java7-playground/target/classes
FOR /L %%I IN (1,1,5) DO FOR /L %%A IN (0,2000,50000) DO FOR %%B IN (10, 100, 500, 1000, 5000) DO java -cp %CLASSPATH% com.schlimm.java7.nio.threadpools.ThreadPoolPerformance e:/temp/results.out IO CACHED %%A %%B
PAUSE