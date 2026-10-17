@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ========================================================
echo   GIAI PHONG CONG 8080 VA 8081 (DUNG CAC SERVER CU)
echo ========================================================

for /f "tokens=5" %%a in ('netstat -aon ^| findstr /r ":8080 .*LISTENING"') do (
    echo [*] Dang dung tien trinh PID %%a tren cong 8080...
    taskkill /f /pid %%a >nul 2>&1
)

for /f "tokens=5" %%a in ('netstat -aon ^| findstr /r ":8081 .*LISTENING"') do (
    echo [*] Dang dung tien trinh PID %%a tren cong 8081...
    taskkill /f /pid %%a >nul 2>&1
)

echo [OK] Da giai phong thanh cong! Ban co the bat lai Server.
timeout /t 3 >nul