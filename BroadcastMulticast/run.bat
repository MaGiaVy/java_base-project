@echo off
chcp 65001 >nul
echo ===========================================
echo   Broadcast ^& Multicast Demo - Java GUI
echo ===========================================
echo.

:: Compile nếu chưa có .class
if not exist NetworkDemo.class (
    echo [COMPILE] Đang biên dịch...
    javac -encoding UTF-8 *.java
    if errorlevel 1 (
        echo [LỖI] Biên dịch thất bại!
        pause
        exit /b 1
    )
    echo [OK] Biên dịch xong.
    echo.
)

echo [CHẠY] Khởi động NetworkDemo...
java -Dfile.encoding=UTF-8 NetworkDemo
pause
