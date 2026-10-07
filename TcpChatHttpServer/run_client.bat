@echo off
chcp 65001 >nul
title TCP Chat Client (GUI Mode)
echo [RUN] Đang mở giao diện TCP Chat Client...
start java -Dfile.encoding=UTF-8 -cp . TcpChatClient localhost 5000
