#!/bin/sh
set -e	# exit on first failed command
set -u	# exit if encounter never set variable


#--------------------------------------------------
# Makes a jar file of all the class files in this project.
#--------------------------------------------------


#----------define global environment variables


. globalEnvVars.sh


#----------define environment variables specific to this file


jarFile=$libDir/bb.jar

classFiles=$scriptDir/javaClassFiles.txt


#----------list class files


$bt  -listFiles  -root $classDir  -output $classFiles  -include .class  -exclude \$UnitTest  $btOutputLevel


#----------make jar file


$btexec  "cd $classDir && jar  cvf  $jarFile  @$classFiles"
$btexec  "cd $resourceDir && jar  uvf  $jarFile  *"
#$btexec  "cd $srcDir && jar  uvf  $jarFile  *"

# Note: another way to do the above is:
#	A standard way to copy directories is to first compress files in dir1 to standard out, then extract from standard in to dir2 (omitting f from both jar commands):
#	C:\Java> (cd dir1 ; jar c .) | (cd dir2 ; jar x)
# (text copied from the jar javadocs)


#----------index the jar file


$btexec  "cd $libDir && jar  -i  $jarFile"
#--------------------CRITICAL: must cd to libDir before execute jar -i because the jar file may have references to other jar files that are relative to its own location


#----------print out the jar file's contents


$btexec  "jar  tvf  $jarFile"


#----------final actions


. cleanUp.sh
