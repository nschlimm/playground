ECHO OFF
SET CLASSPATH=d:/dev_home/repositories/git/playground/java7-playground/target/classes
FOR /L %%A IN (0,2000,50000) DO FOR %%B IN (10, 100, 500, 1000, 5000) DO java -cp %CLASSPATH% com.schlimm.java7.nio.threadpools.ThreadPoolPerformance e:/temp/results.out SLEEP CACHED %%A %%B
PAUSE