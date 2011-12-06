::--------------------------------------------------
:: Defines global environmental variables.
::
:: WARNING: when set the variables below, DO NOT DO A BLANK ASSIGNMENT like set someVar=
:: if someVar is not currently an environmental variable, since in dos that will set errorlevel to 1
::--------------------------------------------------


::----------suppress echo


@echo off


::----------external resource variables


set libSharedDir="..\lib"


::----------project variables


set rootDir=..
set classDir=%rootDir%\class
set docDir=%rootDir%\doc
set libDir=%rootDir%\lib
set resourceDir=%rootDir%\resource
set scriptDir=%rootDir%\script
set srcDir=%rootDir%\src

set classPath=%classDir%
set classPath=%classPath%;%libSharedDir%\commons-compress-2009-02-10.jar
set classPath=%classPath%;%libSharedDir%\jama.jar
set classPath=%classPath%;%libSharedDir%\javamail-1.4.2.jar
::set classPath=%classPath%;%libSharedDir%\jip-profile.jar
set classPath=%classPath%;%libSharedDir%\jsci-core.jar
set classPath=%classPath%;%libSharedDir%\junit-4.6.jar
set classPath=%classPath%;%libSharedDir%\lma-1.3.jar
set classPath=%classPath%;%libSharedDir%\mt-13.jar
set classPath=%classPath%;%libSharedDir%\servlet-api-2.5.jar
set classPath=%classPath%;%resourceDir%


::----------BuildTool variables


:: set if is currently unset; otherwise keep current value; this allows user to change it on the command line
if not "%btOutputLevel%"=="" goto endIf
	::set btOutputLevel=-quiet
	set btOutputLevel=-verbose
:endIf

set bt=java  -jar bt.jar

set shell=cmd /c
::--------------------CRITICAL: /c causes cmd to return after done executing the command; if leave off, would hang indefinitely

set btexec=%bt%  %btOutputLevel%  -exec  %shell%
