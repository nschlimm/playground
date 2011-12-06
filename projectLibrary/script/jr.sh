#!/bin/sh
set -e	# exit on first failed command
set -u	# exit if encounter never set variable


#--------------------------------------------------
# Runs this project's Java program(s).
#--------------------------------------------------


#----------define global environment variables


. ./globalEnvVars.sh


#----------define environment variables specific to this file


assertions="-enableassertions  -enablesystemassertions"
#assertions="-disableassertions  -disablesystemassertions"

errorHandling=-XX:+ShowMessageBoxOnError
#errorHandling=-XX:OnError=\"gdb\ -\ %p\"
# +++ commented out the above and fell back to first line because it fails in cygwin due to bash splitting it into multiple words; not sure why, as this should work...
#--------------------To understand the above, see:
#	http://java.sun.com/j2se/1.5/pdf/jdk50_ts_guide.pdf Section 3.2.4 starting at page 40 and 121
#	http://support.microsoft.com/?kbid=241215
#	http://download.microsoft.com/download/win2000srv/Utility/3.0/NT45/EN-US/Oem3sr2.zip
#	http://www-128.ibm.com/developerworks/java/library/j-tiger03175.html
errorHandling="$errorHandling  -XX:+HeapDumpOnOutOfMemoryError"

#--------------------throughput collectors:
#gcType="-XX:+AggressiveHeap"
gcType="-XX:+UseParallelGC  -XX:+UseParallelOldGC"
#--------------------low pause collectors:
#gcType="-XX:MaxGCPauseMillis=nnn"
#gcType="-XX:+UseConcMarkSweepGC  -XX:+UseParNewGC"
#gcType=-XX:+UnlockExperimentalVMOptions  -XX:+UseG1GC  -XX:MaxGCPauseMillis=nnn  -XX:GCPauseIntervalMillis=<X>  see http://java.sun.com/javase/6/webnotes/6u14.html
#--------------------gc logging:
#gcType="-Xloggc:./gcLog.txt  -XX:+PrintGCDetails  -XX:+PrintGCTimeStamps"
#--------------------documentation on the gc options above:
#	http://java.sun.com/javase/technologies/hotspot/gc/index.jsp
#	http://java.sun.com/javase/technologies/hotspot/gc/gc_tuning_6.html
#	http://java.sun.com/j2se/1.5.0/docs/guide/vm/gc-ergonomics.html
#	http://www.folgmann.de/en/j2ee/gc.html
#	http://java.sun.com/docs/hotspot/gc1.4.2/faq.html

jni=-Xcheck:jni

logConfig=-Djava.util.logging.config.file=./logging.properties

memory=""
#memory="-Xms64m -Xmx1250m"

performance=""
#performance=-XX:+AggressiveOpts
#performance=$performance  -XX:+DoEscapeAnalysis  see http://java.sun.com/javase/6/webnotes/6u14.html

#vmType=-client
vmType=-server

standardJavaOptions="$assertions  -classpath $classPath  $errorHandling  $gcType  $jni  $logConfig  $memory  $performance  $vmType  -Xfuture"


profiling=""
#profiling=-agentlib:hprof=heap=sites,cpu=samples,depth=20,lineno=y,monitor=y,thread=y,doe=y,file=hprof.txt
#profiling=-javaagent:$libSharedDir/jip-profile.jar


#--------------------start something with low priority (e.g. use if program is making machine unresponsive when run at higher priority):
startWithLowPriority="nice  -n 19"
#--------------------typical usage: $startWithLowPriority java ...


#----------run program(s)

echo
echo "=================================================="
echo

#--------------------unit tests executed using JUnit 4:

root=../class

packageReqs=""
#packageReqs="-packages bb.*"
#packageReqs="-packages bb\.io,bb\.net,bb\.science,bb\.util,bb\.util\.logging"

classReqs=""
#classReqs="-classes Mem.*"
#classReqs="-classes MemoryMonitor\\\$UnitTest"

#methodReqs=""
methodReqs="-methods test_.+"
#methodReqs="-methods test_autocorrelationStatisticsMethods"

java  $standardJavaOptions  bb.util.JUnitExecutor  -root $root  $packageReqs  $classReqs  $methodReqs
#java  $standardJavaOptions  $profiling  bb.util.JUnitExecutor  -root $root  $packageReqs  $classReqs  $methodReqs
#$startWithLowPriority  java  $standardJavaOptions  bb.util.JUnitExecutor  -root $root  -classes Bootstrap\\\$UnitTest

#--------------------unit tests that must be run manually (mostly GUI classes):

#java  $standardJavaOptions  bb.gui.BasicStrokeSerializer\$UnitTest
#java  $standardJavaOptions  bb.gui.CenterLayout\$UnitTest
#java  $standardJavaOptions  bb.gui.DialogInputSecure\$UnitTest
#java  $standardJavaOptions  bb.gui.Displayer\$UnitTest
#java  $standardJavaOptions  bb.gui.DocumentLimitedLength\$UnitTest
#java  $standardJavaOptions  bb.gui.FontUtil\$UnitTest
#java  $standardJavaOptions  bb.gui.GraphicsLabel\$UnitTest
#java  $standardJavaOptions  bb.gui.JLabel2\$UnitTest
#java  $standardJavaOptions  bb.gui.JPanel2\$UnitTest
#java  $standardJavaOptions  bb.gui.LineLayout\$UnitTest
#java  $standardJavaOptions  bb.gui.LookAndFeelDialog\$UnitTest
#java  $standardJavaOptions  bb.gui.MessageDialog\$UnitTest
#java  $standardJavaOptions  bb.gui.RectangleCanvas\$UnitTest
#java  $standardJavaOptions  bb.gui.ScreenShot\$UnitTest
#java  $standardJavaOptions  bb.gui.Sounds\$UnitTest
#java  $standardJavaOptions  bb.gui.SoundUtil\$UnitTest
#java  $standardJavaOptions  bb.gui.ThrowableDialog\$UnitTest
#java  $standardJavaOptions  bb.io.ConsoleUtil\$UnitTest
#java  $standardJavaOptions  bb.util.Execute\$UnitTest
#java  $standardJavaOptions  d.g\$UnitTest

#--------------------other classes:

#java  $standardJavaOptions  bb.gui.SoundUtil  ../resource
#java  $standardJavaOptions  bb.io.TarUtil  -tarFile ../log/test.tar  -pathsToArchive ../class,../src  -filter bb.io.filefilter.OmniFilter
#java  $standardJavaOptions  bb.io.TarUtil  -tarFile ../log/test.tar  -directoryExtraction ../log/tarExtractOutput  -overwrite true
#java  $standardJavaOptions  bb.io.ZipUtil  -zipFile E:/backup/dataFinancial/realTime/iqFeed/iqFeed_2009-01-13.zip  -listContents
#java  $standardJavaOptions  bb.io.ZipUtil  -zipFile ../log/test.zip  -pathsToArchive ../class,../src  -filter bb.io.filefilter.OmniFilter
#java  $standardJavaOptions  bb.io.ZipUtil  -zipFile ../log/test.zip  -directoryExtraction ../log/zipExtractOutput  -overwrite true
#java  $standardJavaOptions  bb.util.CommandLineInterface
#java  $standardJavaOptions  bb.util.EncryptUtil  -fileInput <fileInput>  -fileOutput <fileOutput>  -operation encryptOperation  -algorithm ENCRYPTION_ALGORITHM_DEFAULT  -fileSaltTarget <fileSaltTarget>  -fileIterationCount  <fileIterationCount>  -passwordInstruction  "Enter some password below.  Note that character echo is suppressed."  -password abracadabra

# TO DO CODE PROFILING, read: D:/software/java/docsGroupedByTopic/performance/profiling.txt
