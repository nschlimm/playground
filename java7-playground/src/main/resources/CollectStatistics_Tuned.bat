ECHO OFF
SET CLASSPATH=d:/dev_home/repositories/git/playground/java7-playground/target/classes
FOR /L %%I IN (1,1,1) DO FOR /L %%A IN (0,2000,20000) DO FOR %%B IN (100) DO java -cp %CLASSPATH% com.schlimm.java7.nio.threadpools.ThreadPoolPerformance e:/temp/results.out IO2 CACHED_TUNED %%A %%B
FOR /L %%I IN (1,1,1) DO FOR /L %%A IN (0,2000,20000) DO FOR %%B IN (100) DO java -cp %CLASSPATH% com.schlimm.java7.nio.threadpools.ThreadPoolPerformance e:/temp/results.out IO2 FIXED_TUNED %%A %%B
PAUSE