@echo off
cd /d "%~dp0"
title SGU FTP Server - Standalone (Port 2121)

echo =======================================================
echo   SGU FTP SERVER - STANDALONE (PORT 2121)
echo   Ready for FileZilla Client testing
echo =======================================================
echo.

if not exist "bin\ui\FtpServerApp.class" (
    echo Compiling Java source files...
    javac -encoding UTF-8 -d bin src\common\*.java src\server\*.java src\client\*.java src\ui\*.java
)

echo Launching SGU FTP Server GUI...
echo Please do not close this console window while running the server.
echo.
java -cp bin ui.FtpServerApp
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Server exited with code: %errorlevel%
    pause
)
