ECHO OFF
SET CLASSPATH=d:/dev_home/repositories/git/playground/java7-playground/target/classes
REM FOR /L %%I IN (1,1,1) DO FOR /L %%A IN (0,2000,60000) DO FOR %%B IN (30) DO java -cp %CLASSPATH% com.schlimm.java7.nio.threadpools.ThreadPoolPerformance e:/temp/results.out IO2 CACHED %%A %%B
REM FOR /L %%I IN (1,1,1) DO FOR /L %%A IN (0,2000,60000) DO FOR %%B IN (30) DO java -cp %CLASSPATH% com.schlimm.java7.nio.threadpools.ThreadPoolPerformance e:/temp/results.out IO2 FIXED %%A %%B
REM FOR /L %%I IN (1,1,1) DO FOR /L %%A IN (0,2000,60000) DO FOR %%B IN (30) DO java -cp %CLASSPATH% com.schlimm.java7.nio.threadpools.ThreadPoolPerformance e:/temp/results.out IO CACHED %%A %%B
FOR /L %%I IN (1,1,1) DO FOR /L %%A IN (0,2000,60000) DO FOR %%B IN (30) DO java -cp %CLASSPATH% com.schlimm.java7.nio.threadpools.ThreadPoolPerformance e:/temp/results.out IO FIXED %%A %%B
FOR /L %%I IN (1,1,1) DO FOR /L %%A IN (0,2000,60000) DO FOR %%B IN (30) DO java -cp %CLASSPATH% com.schlimm.java7.nio.threadpools.ThreadPoolPerformance e:/temp/results.out IO FIXED_TUNED %%A %%B
FOR /L %%I IN (1,1,1) DO FOR /L %%A IN (0,2000,60000) DO FOR %%B IN (30) DO java -cp %CLASSPATH% com.schlimm.java7.nio.threadpools.ThreadPoolPerformance e:/temp/results.out IO CACHED_TUNED %%A %%B
FOR /L %%I IN (1,1,1) DO FOR /L %%A IN (0,2000,60000) DO FOR %%B IN (30) DO java -cp %CLASSPATH% com.schlimm.java7.nio.threadpools.ThreadPoolPerformance e:/temp/results.out IO2 FIXED_TUNED %%A %%B
FOR /L %%I IN (1,1,1) DO FOR /L %%A IN (0,2000,60000) DO FOR %%B IN (30) DO java -cp %CLASSPATH% com.schlimm.java7.nio.threadpools.ThreadPoolPerformance e:/temp/results.out IO2 CACHED_TUNED %%A %%B
PAUSE