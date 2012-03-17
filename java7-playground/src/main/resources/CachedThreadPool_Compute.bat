ECHO OFF
SET CLASSPATH=d:/dev_home/repositories/git/playground/java7-playground/target/classes
FOR /L %%I IN (1,1,5) DO FOR /L %%A IN (0,2000,50000) DO FOR %%B IN (100, 1000, 10000, 100000, 1000000) DO java -cp %CLASSPATH% com.schlimm.java7.nio.threadpools.ThreadPoolPerformance e:/temp/results.out COMPUTE CACHED %%A %%B
PAUSE