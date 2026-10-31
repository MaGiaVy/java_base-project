@echo off
cd /d "%~dp0"
title SGU FTP Client - Standalone (Port 21 / 2121)

echo =======================================================
echo   SGU FTP CLIENT - STANDALONE (GUI)
echo   Connect to Windows IIS (Port 21) or Java FTP Server
echo =======================================================
echo.

if not exist "bin\ui\FtpClientApp.class" (
    echo Compiling Java source files...
    javac -encoding UTF-8 -d bin src\common\*.java src\server\*.java src\client\*.java src\ui\*.java
)

echo Launching SGU FTP Client GUI...
echo Please do not close this console window while using the client.
echo.
java -cp bin ui.FtpClientApp
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Client exited with code: %errorlevel%
    pause
)
