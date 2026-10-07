@echo off
chcp 65001 >nul
title TCP Chat Client (Console Mode)
echo ======================================================
echo   📡 TCP CHAT CLIENT (DÒNG LỆNH CONSOLE)
echo ======================================================
echo.
java -Dfile.encoding=UTF-8 -cp . TcpChatClient localhost 5000 --console
pause
