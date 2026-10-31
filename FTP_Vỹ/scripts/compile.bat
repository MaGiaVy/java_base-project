@echo off
chcp 65001 > nul
cd /d "%~dp0\.."

echo ========================================================
echo   SGU FTP PROJECT - BIÊN DỊCH MÃ NGUỒN JAVA
echo ========================================================
echo [1/2] Đang kiểm tra và tạo thư mục bin...
if not exist bin mkdir bin

echo [2/2] Đang biên dịch các module: common, server, client, ui, test...
javac -encoding UTF-8 -d bin src\common\*.java src\server\*.java src\client\*.java src\ui\*.java src\test\*.java

if %errorlevel% neq 0 (
    echo.
    echo [LỖI] Quá trình biên dịch thất bại! Vui lòng kiểm tra lỗi bên trên.
    pause
    exit /b %errorlevel%
)

echo.
echo [THÀNH CÔNG] Toàn bộ mã nguồn đã được biên dịch vào thư mục bin!
echo ========================================================
pause
