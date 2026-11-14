@echo off
cd /d "%~dp0"
javac -encoding UTF-8 MulticastChatGUI.java
if errorlevel 1 (pause & exit /b 1)
start "" javaw MulticastChatGUI
