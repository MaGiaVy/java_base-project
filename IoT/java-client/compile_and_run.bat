@echo off
chcp 65001 >nul
echo ========================================================
echo   BIÊN DỊCH VÀ KHỞI CHẠY ỨNG DỤNG JAVA IOT CLIENT
echo ========================================================
echo.

set JAVA_DIR=%~dp0
cd /d "%JAVA_DIR%"

if not exist "bin" mkdir bin

echo [1/2] Đang biên dịch mã nguồn Java...
javac -encoding UTF-8 -cp "lib/*" -d bin src/com/iot/*.java

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [LỖI] Quá trình biên dịch Java thất bại!
    pause
    exit /b %ERRORLEVEL%
)

echo [2/2] Biên dịch thành công! Đang khởi chạy giao diện Swing Client...
start "" javaw -cp "bin;lib/*" com.iot.App

echo [HOÀN TẤT] Ứng dụng Java Client đã được mở.
