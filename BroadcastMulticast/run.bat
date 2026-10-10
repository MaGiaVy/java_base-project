@echo off
chcp 65001 >nul
echo ========================================================
echo   UDP Unified Chat (Gộp Broadcast ^& Multicast) - Java GUI
echo ========================================================
echo.

echo [COMPILE] Đang biên dịch các file Java...
javac -encoding UTF-8 *.java
if errorlevel 1 (
    echo [LỖI] Biên dịch thất bại! Vui lòng kiểm tra mã nguồn.
    pause
    exit /b 1
)
echo [OK] Biên dịch hoàn tất!
echo.

echo [CHẠY] Khởi động UnifiedChatGUI...
java -Dfile.encoding=UTF-8 NetworkDemo
pause
