@echo off
rem Baut zero-agent.jar aus agent_src\
rem Braucht JDK 17+ (javac + jar muessen im PATH oder JAVA_HOME gesetzt sein)
cd /d "%~dp0"

set "JDK=C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot"
if exist "%JDK%\bin\javac.exe" (
    set "JAVAC=%JDK%\bin\javac.exe"
    set "JARCMD=%JDK%\bin\jar.exe"
    goto :build
)
if defined JAVA_HOME (
    set "JAVAC=%JAVA_HOME%\bin\javac.exe"
    set "JARCMD=%JAVA_HOME%\bin\jar.exe"
    goto :build
)
where javac >nul 2>&1
if %errorlevel%==0 ( set "JAVAC=javac" & set "JARCMD=jar" & goto :build )
echo Kein JDK gefunden. JAVA_HOME setzen oder JDK installieren.
pause & exit /b 1

:build
if exist agent_build rmdir /s /q agent_build
mkdir agent_build

"%JAVAC%" --add-modules jdk.attach -d agent_build ^
    agent_src\dev\zeroinject\Agent.java ^
    agent_src\dev\zeroinject\Injector.java
if errorlevel 1 ( echo Compile-Fehler! & pause & exit /b 1 )

(
echo Manifest-Version: 1.0
echo Agent-Class: dev.zeroinject.Agent
echo Premain-Class: dev.zeroinject.Agent
echo Can-Redefine-Classes: false
echo Can-Retransform-Classes: false
echo Main-Class: dev.zeroinject.Injector
echo.
) > agent_build\MANIFEST.MF

"%JARCMD%" cfm zero-agent.jar agent_build\MANIFEST.MF -C agent_build .
if errorlevel 1 ( echo JAR-Fehler! & pause & exit /b 1 )

rmdir /s /q agent_build
echo.
echo zero-agent.jar fertig!
echo Jetzt noch zero-loader.exe neu bauen (Build.bat im loader-Ordner)
echo damit die neue zero-agent.jar reinkopiert wird.
pause
