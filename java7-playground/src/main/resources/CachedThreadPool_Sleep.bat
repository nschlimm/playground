ECHO OFF
SET CLASSPATH=E:/dev_home/repositories/git/playground/java7-playground/target/classes
FOR /L %%A IN (0,100,5000) DO FOR %%B IN (100,500,1000) DO java -cp %CLASSPATH% com.schlimm.java7.nio.threadpools.ThreadPoolPerformance e:/temp/results.out SLEEP CACHED %%A %%B
PAUSE