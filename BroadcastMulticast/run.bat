@echo off
chcp 65001 >nul
echo ========================================================
echo   UDP Unified Chat (Gộp Broadcast ^& Multicast) - Java GUI
echo ========================================================
echo.

:: Giải phóng cổng 7000 và 8000 nếu có tiến trình cũ bị treo ngầm
for /f "tokens=5" %%a in ('netstat -aon ^| findstr /r "7000.*UDP 8000.*UDP" 2^>nul') do (
    if not "%%a"=="0" taskkill /F /PID %%a >nul 2>&1
)

echo [COMPILE] Đang biên dịch các file Java...
javac -encoding UTF-8 *.java
if errorlevel 1 (
    echo [LỖI] Biên dịch thất bại! Vui lòng kiểm tra mã nguồn.
    pause
    exit /b 1
)
echo [OK] Biên dịch hoàn tất!
echo.

echo [CHẠY] Khởi động Unified Chat (ưu tiên IPv4)...
java -Dfile.encoding=UTF-8 -Djava.net.preferIPv4Stack=true NetworkDemo
pause
