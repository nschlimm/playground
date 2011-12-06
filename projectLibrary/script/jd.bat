::--------------------------------------------------
:: Makes javadocs for all the classes in this project.
::
:: WARNING: when set the variables below, DO NOT DO A BLANK ASSIGNMENT like set someVar=
:: if someVar is not currently an environmental variable, since in dos that will set errorlevel to 1
::--------------------------------------------------


::----------define global environment variables


@call globalEnvVars.bat
if errorlevel 1 goto handleError


::----------define environment variables specific to this file


set javadocDir=%docDir%\javadoc

set jdkVersion=-source 1.6

set links=-link http://java.sun.com/javase/6/docs/api/
set links=%links%  -link http://java.sun.com/javaee/5/docs/api/
set links=%links%  -link http://junit.sourceforge.net/javadoc_40

::set outputLevel=-verbose
set outputLevel=-quiet

set private=-private

::--------------------added the line below to handle non-ascii characters (found this to be an issue when compile on unix boxes whose default encoding is ascii); see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5046139
::set sourceEncoding=-encoding cp1252
set sourceEncoding=-encoding ISO-8859-1

set standardJavadocOptions=-author  -breakiterator  -classpath %classPath%  -d %javadocDir%  %jdkVersion%  %links%  %outputLevel%  -overview %srcDir%\overview.html  %private%  -serialwarn  %sourceEncoding%  -sourcepath %srcDir%  -use

set packageFile=%scriptDir%\javaPackages.txt

if errorlevel 1 goto handleError


::----------identify Java packages


%bt%  -listPackages  -root %srcDir%  -output %packageFile%  -exclude alternativeVersions  %btOutputLevel%
if errorlevel 1 goto handleError


::----------generate javadocs


%btexec%  javadoc  %standardJavadocOptions%  @%packageFile%
if errorlevel 1 goto handleError


::----------error handling and final actions:


:handleError
if not errorlevel 1 goto finalActions
echo.
echo ERROR DETECTED: jd.bat will TERMINATE PREMATURELY

:finalActions
set exitCode=%errorlevel%
@call cleanUp.bat
exit /b %exitCode%
