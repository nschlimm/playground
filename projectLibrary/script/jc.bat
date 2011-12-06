::--------------------------------------------------
:: Compiles the project.
::
:: The first action, however, is that the error level is cleared (set to 0),
:: so that any previous error in this shell instance does not prevent this file from executing.
:: This is convenient during development because compilation errors (which will set the error level > 0) are frequent.
::
:: WARNING: when set the variables below, DO NOT DO A BLANK ASSIGNMENT like set someVar=
:: if someVar is not currently an environmental variable, since in dos that will set errorlevel to 1
::
:: Note: to execute a chain of commands (including bat files) in dos on a single command line,
:: use the && functionality, for instance, jc && jd (compile first, then javadoc).
::--------------------------------------------------


::----------clear errorlevel
@ver > nul
::--------------------since ver should always execute successfully, it should set errorlevel to 0; redirect output to nul to suppress it from the screen


::----------define global environment variables


@call globalEnvVars.bat
if errorlevel 1 goto handleError


::----------define environment variables specific to this file


set gutMode=-strict
::set gutMode=-warn

set checks=-Xlint:all
 
::set javacOutput=-Xstdout javacOutput.txt

set jdkVersion=-source 1.6  -target 1.6

::--------------------added the line below to handle non-ascii characters (found this to be an issue when compile on unix boxes whose default encoding is ascii); see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5046139
::set sourceEncoding=-encoding cp1252
set sourceEncoding=-encoding ISO-8859-1

set srcFiles=%scriptDir%\javaSourceFiles.txt

if errorlevel 1 goto handleError


::----------gut the class directory


%bt%  -gut  -root %classDir%  -extensions .class  %gutMode%  %btOutputLevel%
if errorlevel 1 goto handleError


::----------list .java files


%bt%  -listFiles  -root %srcDir%  -output %srcFiles%  -include .java  -exclude .nc  %btOutputLevel%
if errorlevel 1 goto handleError


::----------compile


%btexec%  "cd /d %srcDir% && javac  -classpath %classPath%  -sourcepath %srcDir%  -d %classDir%  %checks%  %javacOutput%  %jdkVersion% %sourceEncoding%  @%srcFiles%"
if errorlevel 1 goto handleError

:: NOTE: the format of srcFiles (no parent directories beyond the package level) is what requires us to temporarily change directory above

:: NOTE: javac does not emit a non-zero exit code for warnings, so there is no way to tell that they have occurred from the above.
:: See my RFE on this issue: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6296560
:: and see the evaluation, which mentions these JSRs to address the issues:
:: http://jcp.org/aboutJava/communityprocess/edr/jsr199/
:: http://jcp.org/en/jsr/detail?id=199


::----------error handling and final actions:


:handleError
if not errorlevel 1 goto finalActions
echo.
echo ERROR DETECTED: jc.bat will TERMINATE PREMATURELY

:finalActions
set exitCode=%errorlevel%
@call cleanUp.bat
exit /b %exitCode%
