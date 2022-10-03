@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.

@REM -----------------------------------------------------------------------------
@REM Apache Maven Startup Script
@REM
@REM Environment Variable Prerequisites
@REM
@REM   JAVA_HOME          Must point at your Java Development Kit installation.
@REM   MAVEN_BATCH_ECHO  (Optional) Set to 'on' to enable the echoing of the batch commands.
@REM   MAVEN_BATCH_PAUSE (Optional) set to 'on' to wait for a key stroke before ending.
@REM   MAVEN_OPTS        (Optional) Java runtime options used when Maven is executed.
@REM   MAVEN_SKIP_RC     (Optional) Flag to disable loading of mavenrc files.
@REM -----------------------------------------------------------------------------

@REM Begin all REM lines with '@' in case MAVEN_BATCH_ECHO is 'on'
@echo off
@REM enable echoing my setting MAVEN_BATCH_ECHO to 'on'
@if "%MAVEN_BATCH_ECHO%"=="on" echo %MAVEN_BATCH_ECHO%

@REM Execute a user defined script before this one
if not "%MAVEN_SKIP_RC%"=="" goto skipRcPre
@REM check for pre script, once with legacy .bat ending and once with .cmd ending
if exist "%USERPROFILE%\mavenrc_pre.bat" call "%USERPROFILE%\mavenrc_pre.bat"
if exist "%USERPROFILE%\mavenrc_pre.cmd" call "%USERPROFILE%\mavenrc_pre.cmd"
:skipRcPre

@setlocal

set ERROR_CODE=0

@REM ==== START VALIDATION ====
if not "%JAVA_HOME%"=="" goto OkJHome
for %%i in (java.exe) do set "JAVACMD=%%~$PATH:i"
goto checkJCmd

:OkJHome
set "JAVACMD=%JAVA_HOME%\bin\java.exe"

:checkJCmd
if exist "%JAVACMD%" goto chkMHome

echo The JAVA_HOME environment variable is not defined correctly >&2
echo This environment variable is needed to run this program >&2
echo NB: JAVA_HOME should point to a JDK not a JRE >&2
goto error

:chkMHome
set "MVND_HOME=%~dp0.."
if not "%MVND_HOME%"=="" goto stripMHome
goto error

:stripMHome
if not "_%MVND_HOME:~-1%"=="_\" goto checkMCmd
set "MVND_HOME=%MVND_HOME:~0,-1%"
goto stripMHome

:checkMCmd
if exist "%MVND_HOME%\bin\mvnd.cmd" goto init
goto error
@REM ==== END VALIDATION ====

:init

set MAVEN_CMD_LINE_ARGS=%*

@setlocal EnableExtensions EnableDelayedExpansion
for %%i in ("%MVND_HOME%"\mvn\boot\*.jar "%MVND_HOME%"\mvn\lib\ext\*.jar "%MVND_HOME%"\mvn\lib\*.jar) do set DAEMON_JAR=!DAEMON_JAR!;%%i
@endlocal & set DAEMON_JAR="%DAEMON_JAR%"

set DAEMON_LAUNCHER=org.mvndaemon.mvnd.client.DefaultClient

"%JAVACMD%" ^
  %JVM_CONFIG_MAVEN_PROPS% ^
  %MAVEN_OPTS% ^
  %MAVEN_DEBUG_OPTS% ^
  -classpath %DAEMON_JAR% ^
  "-Dlogback.configurationFile=%MVND_HOME%\conf\logback-client.xml" ^
  "-Dmvnd.home=%MVND_HOME%" ^
  "-Dmaven.home=%MVND_HOME%\mvn" ^
  "-Dlibrary.jansi.path=%MVND_HOME%\mvn\lib\jansi-native" ^
  "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" ^
  %DAEMON_LAUNCHER% %MAVEN_CMD_LINE_ARGS%
if ERRORLEVEL 1 goto error
goto end

:error
set ERROR_CODE=1

:end
@endlocal & set ERROR_CODE=%ERROR_CODE%

if not "%MAVEN_SKIP_RC%"=="" goto skipRcPost
@REM check for post script, once with legacy .bat ending and once with .cmd ending
if exist "%USERPROFILE%\mavenrc_post.bat" call "%USERPROFILE%\mavenrc_post.bat"
if exist "%USERPROFILE%\mavenrc_post.cmd" call "%USERPROFILE%\mavenrc_post.cmd"
:skipRcPost

@REM pause the script if MAVEN_BATCH_PAUSE is set to 'on'
if "%MAVEN_BATCH_PAUSE%"=="on" pause

if "%MAVEN_TERMINATE_CMD%"=="on" exit %ERROR_CODE%

cmd /C exit /B %ERROR_CODE%
