#!/bin/sh
set -e	# exit on first failed command
set -u	# exit if encounter never set variable


#--------------------------------------------------
# Compiles the project.
#
# Note: to execute a chain of commands (including sh files) in sh on a single command line,
# use the && functionality, for instance, sh jc.sh && sh jd.sh (compile first, then javadoc).
#--------------------------------------------------


#----------clear errorlevel
# no need: sh scripts seem to always have an initial exit code of 0 when they start executing; they do not seem to be initialized with the exit code of any script that ran before them; see D:\interestsTechnical\computerNotes\operatingSystems\unix\shellScripts.txt


#----------define global environment variables


. globalEnvVars.sh


#----------define environment variables specific to this file


gutMode=-strict
#gutMode=-warn

checks=-Xlint:all

javacOutput=""
#javacOutput="-Xstdout javacOutput.txt"

jdkVersion="-source 1.6  -target 1.6"

#--------------------added the line below to handle non-ascii characters (found this to be an issue when compile on unix boxes whose default encoding is ascii); see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5046139
#sourceEncoding="-encoding cp1252"
sourceEncoding="-encoding ISO-8859-1"

srcFiles=$scriptDir/javaSourceFiles.txt


#----------gut the class directory


$bt  -gut  -root $classDir  -extensions .class  $gutMode  $btOutputLevel


#----------list .java files


$bt  -listFiles  -root $srcDir  -output $srcFiles  -include .java  -exclude .nc  $btOutputLevel


#----------compile


if [ "$isCygwin" = "true" ]; then
	$btexec  "\"cd $srcDir && javac  -classpath '$classPath'  -sourcepath $srcDir  -d $classDir  $checks  $javacOutput  $jdkVersion  $sourceEncoding  @$srcFiles\""
else
	$btexec  "cd $srcDir && javac  -classpath $classPath  -sourcepath $srcDir  -d $classDir  $checks  $javacOutput  $jdkVersion  $sourceEncoding  @$srcFiles"
fi

# NOTE: the format of srcFiles (no parent directories beyond the package level) is what requires us to temporarily change directory above

# NOTE: javac does not emit a non-zero exit code for warnings, so there is no way to tell that they have occurred from the above.
# See my RFE on this issue: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6296560
# and see the evaluation, which mentions these JSRs to address the issues:
# http://jcp.org/aboutJava/communityprocess/edr/jsr199/
# http://jcp.org/en/jsr/detail?id=199


#----------final actions


. cleanUp.sh
