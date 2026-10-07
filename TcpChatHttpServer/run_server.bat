@echo off
chcp 65001 >nul
title TCP Chat & HTTP Web Server (Ports 5000 & 8080)
echo ==================================================================
echo   🚀 KHỞI ĐỘNG HỆ THỐNG: TCP CHAT SERVER & HTTP REST API BACKEND
echo ==================================================================
echo.

:: Biên dịch nếu chưa có file .class
if not exist ChatAndWebServer.class (
    echo [COMPILE] Đang biên dịch mã nguồn Java...
    javac -encoding UTF-8 *.java
    if errorlevel 1 (
        echo [LỖI] Biên dịch thất bại! Vui lòng kiểm tra JDK.
        pause
        exit /b 1
    )
    echo [OK] Biên dịch thành công!
    echo.
)

echo [RUN] Đang chạy ChatAndWebServer...
echo 👉 TCP Chat Server: Port 5000
echo 👉 HTTP Web Server: http://localhost:8080/
echo.
java -Dfile.encoding=UTF-8 ChatAndWebServer
pause
