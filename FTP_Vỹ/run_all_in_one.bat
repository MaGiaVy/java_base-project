@echo off
cd /d "%~dp0"
title SGU FTP Suite - All-In-One Dashboard

echo =======================================================
echo   SGU FTP SUITE - ALL-IN-ONE (RFC 959 TCP SOCKET)
echo =======================================================
echo.
echo [1/2] Checking compiled classes...
if not exist "bin\ui\FtpAllInOneApp.class" (
    echo Compiling Java source files...
    javac -encoding UTF-8 -d bin src\common\*.java src\server\*.java src\client\*.java src\ui\*.java
)

echo [2/2] Launching SGU FTP Suite GUI...
echo Please do not close this console window while using the application.
echo.
java -cp bin ui.FtpAllInOneApp
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Application exited with code: %errorlevel%
    pause
)
