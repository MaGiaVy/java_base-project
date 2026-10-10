@echo off
chcp 65001 >nul
title HTTP Chat Client (Port 8080)
echo ==================================================================
echo   🌐 KHỞI ĐỘNG HTTP CHAT CLIENT (REST API CALLER)
echo ==================================================================
echo.

:: Biên dịch nếu chưa có file .class
if not exist HttpChatClient.class (
    echo [COMPILE] Đang biên dịch HttpChatClient.java...
    javac -encoding UTF-8 HttpChatClient.java
    if errorlevel 1 (
        echo [LỖI] Biên dịch thất bại! Vui lòng kiểm tra JDK.
        pause
        exit /b 1
    )
    echo [OK] Biên dịch thành công!
    echo.
)

echo [RUN] Đang chạy HttpChatClient...
echo 👉 Kết nối tới HTTP REST API: http://localhost:8080
echo.
java -Dfile.encoding=UTF-8 HttpChatClient
pause
