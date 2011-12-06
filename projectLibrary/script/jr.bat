::--------------------------------------------------
:: Runs this project's Java program(s).
::
:: WARNING: when set the variables below, DO NOT DO A BLANK ASSIGNMENT like set someVar=
:: if someVar is not currently an environmental variable, since in dos that will set errorlevel to 1
::--------------------------------------------------


::----------define global environment variables


@call globalEnvVars.bat
if errorlevel 1 goto handleError


::----------define environment variables specific to this file


set assertions=-enableassertions  -enablesystemassertions
::set assertions=-disableassertions  -disablesystemassertions

::set errorHandling=-XX:+ShowMessageBoxOnError
set errorHandling=-XX:OnError="userdump.exe %%p"
::--------------------CRITICAL: used %%p above because in dos must escape the % char if use it in an env var
::--------------------To understand the above, see:
::	http://java.sun.com/j2se/1.5/pdf/jdk50_ts_guide.pdf Section 3.2.4 starting at page 40 and 121
::	http://support.microsoft.com/?kbid=241215
::	http://download.microsoft.com/download/win2000srv/Utility/3.0/NT45/EN-US/Oem3sr2.zip
::	http://www-128.ibm.com/developerworks/java/library/j-tiger03175.html
set errorHandling=%errorHandling%  -XX:+HeapDumpOnOutOfMemoryError

::--------------------throughput collectors:
::set gcType=-XX:+AggressiveHeap
set gcType=-XX:+UseParallelGC  -XX:+UseParallelOldGC
::--------------------low pause collectors:
::set gcType=-XX:MaxGCPauseMillis=nnn 
::set gcType=-XX:+UseConcMarkSweepGC  -XX:+UseParNewGC
::set gcType=-XX:+UnlockExperimentalVMOptions  -XX:+UseG1GC  -XX:MaxGCPauseMillis=nnn  -XX:GCPauseIntervalMillis=<X>  see http://java.sun.com/javase/6/webnotes/6u14.html
::--------------------gc logging:
::set gcType=-Xloggc:../log/gcLog.txt  -XX:+PrintGCDetails  -XX:+PrintGCTimeStamps
::--------------------documentation on the gc options above:
::	http://java.sun.com/javase/technologies/hotspot/gc/index.jsp
::	http://java.sun.com/javase/technologies/hotspot/gc/gc_tuning_6.html
::	http://java.sun.com/j2se/1.5.0/docs/guide/vm/gc-ergonomics.html
::	http://www.folgmann.de/en/j2ee/gc.html
::	http://java.sun.com/docs/hotspot/gc1.4.2/faq.html

set jni=-Xcheck:jni

set logConfig=-Djava.util.logging.config.file=./logging.properties
  
::set memory=-Xms64m -Xmx1250m

::set performance=-XX:+AggressiveOpts
::set performance=%performance%  -XX:+DoEscapeAnalysis  see http://java.sun.com/javase/6/webnotes/6u14.html

::set vmType=-client
set vmType=-server

set standardJavaOptions=%assertions%  -classpath %classPath%  %errorHandling%  %gcType%  %jni%  %logConfig%  %memory%  %performance%  %vmType%  -Xfuture


::set profiling=-agentlib:hprof=heap=sites,cpu=samples,depth=20,lineno=y,monitor=y,thread=y,doe=y,file=hprof.txt
::set profiling=-javaagent:%libSharedDir%/jip-profile.jar


::--------------------start something with low priority (e.g. use if program is making machine unresponsive when run at higher priority):
set startWithLowPriority=start /low  /b
::--------------------typical usage: %startWithLowPriority% java ...
::--------------------WARNING: the above breaks java's normal ctrl-C jvm abort behavior; must use TaskManager to kill it; if execute start /? it states that the /b option will cause this behavior for some bizarre reason...
::--------------------To understand the above, see:
::	http://www.velocityreviews.com/forums/t151135-setting-java-runtime-process-priority.html
::	http://smallbusiness.itworld.com/4379/nls_windows_lowpriority060814/page_1.html


if errorlevel 1 goto handleError


::----------run program(s)


echo on

@echo.
@echo ==================================================

::--------------------unit tests executed using JUnit 4:

set root=..\class

set packageReqs=
::set packageReqs=-packages bb.*
::set packageReqs=-packages bb\.io,bb\.net,bb\.science,bb\.util,bb\.util\.logging

::set classReqs=
set classReqs=-classes Emailer\$UnitTest

::set methodReqs=
set methodReqs=-methods test_.+
::set methodReqs=-methods test_onMemoryXXX
::set methodReqs=-methods benchmark_.+

java  %standardJavaOptions%  bb.util.JUnitExecutor  -root %root%  %packageReqs%  %classReqs%  %methodReqs%
::java  %standardJavaOptions%  %profiling%  bb.util.JUnitExecutor  -root %root%  %packageReqs%  %classReqs%  %methodReqs%
::%startWithLowPriority%  java  %standardJavaOptions%  bb.util.JUnitExecutor  -root %root%  -classes Bootstrap\$UnitTest

::--------------------unit tests that must be run manually (mostly GUI classes):

::java  %standardJavaOptions%  bb.gui.BasicStrokeSerializer$UnitTest
::java  %standardJavaOptions%  bb.gui.DialogInputSecure$UnitTest
::java  %standardJavaOptions%  bb.gui.Displayer$UnitTest
::java  %standardJavaOptions%  bb.gui.DocumentLimitedLength$UnitTest
::java  %standardJavaOptions%  bb.gui.FontUtil$UnitTest
::java  %standardJavaOptions%  bb.gui.GraphicsLabel$UnitTest
::java  %standardJavaOptions%  bb.gui.GroupLayout2$UnitTest
::java  %standardJavaOptions%  bb.gui.LinePanel$UnitTest
::java  %standardJavaOptions%  bb.gui.LookAndFeelDialog$UnitTest
::java  %standardJavaOptions%  bb.gui.MessageDialog$UnitTest
::java  %standardJavaOptions%  bb.gui.RectangleCanvas$UnitTest
::java  %standardJavaOptions%  bb.gui.ScreenShot$UnitTest
::java  %standardJavaOptions%  bb.gui.Sounds$UnitTest
::java  %standardJavaOptions%  bb.gui.SoundUtil$UnitTest
::java  %standardJavaOptions%  bb.gui.SwingUtil$UnitTest
::java  %standardJavaOptions%  bb.gui.ThrowableDialog$UnitTest
::java  %standardJavaOptions%  bb.io.ConsoleUtil$UnitTest
::java  %standardJavaOptions%  bb.util.Execute$UnitTest
::java  %standardJavaOptions%  bb.util.logging.HandlerAudio$UnitTest
::java  %standardJavaOptions%  bb.util.logging.HandlerConsole$UnitTest
::java  %standardJavaOptions%  bb.util.logging.HandlerEmail$UnitTest
::java  %standardJavaOptions%  bb.util.logging.HandlerGui$UnitTest
::java  %standardJavaOptions%  d.g$UnitTest

::--------------------other classes:

::java  %standardJavaOptions%  bb.gui.SoundUtil  ../resource
::java  %standardJavaOptions%  bb.io.TarUtil  -tarFile ../log/test.tar  -pathsToArchive ../class,../src  -filter bb.io.filefilter.OmniFilter
::java  %standardJavaOptions%  bb.io.TarUtil  -tarFile ../log/test.tar  -directoryExtraction ../log/tarExtractOutput  -overwrite true
::java  %standardJavaOptions%  bb.io.ZipUtil  -zipFile E:/backup/dataFinancial/realTime/iqFeed/iqFeed_2009-01-13.zip  -listContents
::java  %standardJavaOptions%  bb.io.ZipUtil  -zipFile ../log/test.zip  -pathsToArchive ../class,../src  -filter bb.io.filefilter.OmniFilter
::java  %standardJavaOptions%  bb.io.ZipUtil  -zipFile ../log/test.zip  -directoryExtraction ../log/zipExtractOutput  -overwrite true
::java  %standardJavaOptions%  bb.util.CommandLineInterface
::java  %standardJavaOptions%  bb.util.EncryptUtil  -fileInput <fileInput>  -fileOutput <fileOutput>  -operation encryptOperation  -algorithm ENCRYPTION_ALGORITHM_DEFAULT  -fileSaltTarget <fileSaltTarget>  -fileIterationCount  <fileIterationCount>  -passwordInstruction  "Enter some password below.  Note that character echo is suppressed."  -password abracadabra

::--------------------RESET ALL SYSTEM AND USER Preferences:

::java  %standardJavaOptions%  bb.util.PreferencesUtil

@if errorlevel 1 goto handleError

@echo off

:: TO DO CODE PROFILING, read: D:\software\java\docsGroupedByTopic\performance\profiling.txt


::----------error handling and final actions:


:handleError
@if not errorlevel 1 goto finalActions
@echo.
@echo ERROR DETECTED: jr.bat will TERMINATE PREMATURELY

:finalActions
@echo off
