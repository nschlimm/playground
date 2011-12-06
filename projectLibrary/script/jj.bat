::--------------------------------------------------
:: Makes a jar file of all the class files in this project.
::
:: WARNING: when set the variables below, DO NOT DO A BLANK ASSIGNMENT like set someVar=
:: if someVar is not currently an environmental variable, since in dos that will set errorlevel to 1
::--------------------------------------------------


::----------define global environment variables


@call globalEnvVars.bat
if errorlevel 1 goto handleError


::----------define environment variables specific to this file


set jarFile=%libDir%\bb.jar

set classFiles=%scriptDir%\javaClassFiles.txt

if errorlevel 1 goto handleError


::----------list class files


%bt%  -listFiles  -root %classDir%  -output %classFiles%  -include .class  -exclude $UnitTest  %btOutputLevel%
if errorlevel 1 goto handleError


::----------make jar file


%btexec%  "cd /d %classDir% && jar  cvf  %jarFile%  @%classFiles%"
%btexec%  "cd /d %resourceDir% && jar  uf  %jarFile%  *"
::%btexec%  "cd /d %srcDir% && jar  uvf  %jarFile%  *"
if errorlevel 1 goto handleError

:: Note: another way to do the above is:
::	A standard way to copy directories is to first compress files in dir1 to standard out, then extract from standard in to dir2 (omitting f from both jar commands):
::	C:\Java> (cd dir1; jar c .) | (cd dir2; jar x)
:: (text copied from the jar javadocs)


::----------index the jar file


%btexec%  "cd /d %libDir% && jar  -i  %jarFile%"
::--------------------CRITICAL: must cd to libDir before execute jar -i because the jar file may have references to other jar files that are relative to its own location
if errorlevel 1 goto handleError


::----------print out the jar file's contents


%btexec%  "jar  tvf  %jarFile%"
if errorlevel 1 goto handleError


::----------error handling and final actions:


:handleError
if not errorlevel 1 goto finalActions
echo.
echo ERROR DETECTED: jj.bat will TERMINATE PREMATURELY

:finalActions
set exitCode=%errorlevel%
@call cleanUp.bat
exit /b %exitCode%
