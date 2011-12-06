#!/bin/sh
set -e	# exit on first failed command
set -u	# exit if encounter never set variable


#--------------------------------------------------
# Makes javadocs for all the classes in this project.
#--------------------------------------------------


#----------define global environment variables


. globalEnvVars.sh


#----------define environment variables specific to this file


javadocDir=$docDir/javadoc

jdkVersion="-source 1.6"

links="-link http://java.sun.com/javase/6/docs/api/"
links="$links  -link http://java.sun.com/javaee/5/docs/api/"
links="$links  -link http://junit.sourceforge.net/javadoc_40"

#outputLevel=-verbose
outputLevel=-quiet

private=-private

#--------------------added the line below to handle non-ascii characters (found this to be an issue when compile on unix boxes whose default encoding is ascii); see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5046139
#sourceEncoding="-encoding cp1252"
sourceEncoding="-encoding ISO-8859-1"

standardJavadocOptions="-author  -breakiterator  -classpath '$classPath'  -d $javadocDir  $jdkVersion  $links  $outputLevel  -overview $srcDir/overview.html  $private  -serialwarn  $sourceEncoding  -sourcepath $srcDir  -use"

packageFile=$scriptDir/javaPackages.txt


#----------identify Java packages


$bt  -listPackages  -root $srcDir  -output $packageFile  -exclude alternativeVersions  $btOutputLevel


#----------generate javadocs


$btexec  "javadoc  $standardJavadocOptions  @$packageFile"


#----------final actions


. cleanUp.sh
