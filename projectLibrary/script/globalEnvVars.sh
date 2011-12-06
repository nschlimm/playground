#!/bin/sh
set -e	# exit on first failed command
set -u	# exit if encounter never set variable


#--------------------------------------------------
# Defines global environmental variables and functions.
#--------------------------------------------------


#----------assign isCygwin (will either be the string "true" or the string "false")


# Concerning cygwin autodetection, see http://www.cygwin.com/ml/cygwin/2006-09/msg00653.html
if [ $(uname -s | grep -c CYGWIN) -gt 0 ]; then
	isCygwin=true
else
	isCygwin=false
fi


#----------external resource variables


libSharedDir="../lib"


#----------project variables


rootDir=..
classDir=$rootDir/class
docDir=$rootDir/doc
libDir=$rootDir/lib
resourceDir=$rootDir/resource
scriptDir=$rootDir/script
srcDir=$rootDir/src

classPath=$classDir
classPath=$classPath:$libSharedDir/commons-compress-2009-02-10.jar
classPath=$classPath:$libSharedDir/jama.jar
classPath=$classPath:$libSharedDir/javamail-1.4.2.jar
#classPath=$classPath:$libSharedDir/jip-profile.jar
classPath=$classPath:$libSharedDir/jsci-core.jar
classPath=$classPath:$libSharedDir/junit-4.6.jar
classPath=$classPath:$libSharedDir/lma-1.3.jar
classPath=$classPath:$libSharedDir/mt-13.jar
classPath=$classPath:$libSharedDir/servlet-api-2.5.jar
classPath=$classPath:$resourceDir
if [ "$isCygwin" = "true" ]; then	# CRITICAL: if this is cygwin, must change the classpath to windows form:
	classPath=`cygpath -pw $classPath`
fi


#----------BuildTool variables


btOutputLevel=${btOutputLevel:=-verbose}	# set if is currently unassigned; otherwise keep current value; this allows user to change it on the command line

bt="java  -jar bt.jar"

if [ "$isCygwin" = "true" ]; then
	shell="`cygpath -w /bin/bash.exe` -i -c "
else
	shell="bash -c "	# hmm, would have thought that "bash -i -c " would work, but the -i causes a "bash: no job control in this shell" error on at least one linux box that tested on; looks like others have this problem too: http://www.mail-archive.com/bug-bash@gnu.org/msg02896.html
fi

btexec="$bt  $btOutputLevel  -exec  $shell"
