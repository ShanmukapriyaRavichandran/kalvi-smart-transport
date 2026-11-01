@echo off
title Kalvi College Smart Transport Web Platform
cd /d "%~dp0"

echo ========================================================================
echo       KALVI COLLEGE SMART TRANSPORT MANAGEMENT SYSTEM (JAVA WEB)
echo ========================================================================
echo.

:: 1. Free Port 8080 if an old instance is already running
for /f "tokens=5" %%a in ('netstat -aon 2^>nul ^| findstr ":8080" ^| findstr "LISTENING"') do (
    echo [INFO] Stopping previous server instance on port 8080 - PID %%a
    taskkill /f /pid %%a >nul 2>&1
)

:: 2. Find Java compiler and runtime
set "JAVAC_EXE=javac"
set "JAVA_EXE=java"

if exist "%JAVA_HOME%\bin\javac.exe" (
    set "JAVAC_EXE=%JAVA_HOME%\bin\javac.exe"
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else if exist "C:\Program Files\Java\jdk-21.0.12\bin\javac.exe" (
    set "JAVAC_EXE=C:\Program Files\Java\jdk-21.0.12\bin\javac.exe"
    set "JAVA_EXE=C:\Program Files\Java\jdk-21.0.12\bin\java.exe"
) else if exist "C:\Program Files\Java\latest\bin\javac.exe" (
    set "JAVAC_EXE=C:\Program Files\Java\latest\bin\javac.exe"
    set "JAVA_EXE=C:\Program Files\Java\latest\bin\java.exe"
)

echo [1/2] Compiling Java classes with javac...
if not exist "bin" mkdir "bin"

"%JAVAC_EXE%" -cp "lib/*;src" -d "bin" src\DatabaseManager.java src\Dijkstra.java src\TransportServer.java src\Main.java
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Compilation failed! Please check Java errors above.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [2/2] Launching Transport Web Server and Terminal Console...
echo       - Server URL:      http://localhost:8080
echo       - OpenStreetMap and OSRM Real Road Routing Engine
echo       - Dynamic Detours and Role-Based Authentication
echo ========================================================================
echo.

"%JAVA_EXE%" -cp "lib/*;bin" Main

pause
