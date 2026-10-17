@echo off
chcp 65001 >nul
title Sao chep Web Portal va cac file HTML vao C:\inetpub\wwwroot (IIS)
echo ==================================================================
echo   🚀 COPY WEB PORTAL (index.html) & P1, P2 VAO THU MUC IIS
echo   📁 Dich den: C:\inetpub\wwwroot
echo ==================================================================
echo.

:: Kiem tra quyen Administrator
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo [INFO] Dang xin quyen Administrator de chep file vao C:\inetpub\wwwroot...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process powershell -ArgumentList '-NoProfile -Command Copy-Item -Path ''%~dp0*.html'',''%~dp0*.htm'',''%~dp0*.HTM'' -Destination ''C:\inetpub\wwwroot\'' -Force; Write-Host ''[OK] Da chep thanh cong index.html va cac file HTML vao C:\inetpub\wwwroot!'' -ForegroundColor Green; Start-Sleep -Seconds 2' -Verb RunAs"
    exit /b 0
)

copy /y "%~dp0*.html" "C:\inetpub\wwwroot\"
copy /y "%~dp0*.htm" "C:\inetpub\wwwroot\" >nul 2>&1
copy /y "%~dp0*.HTM" "C:\inetpub\wwwroot\" >nul 2>&1

echo.
echo ==================================================================
echo   ✅ DA SAO CHEP THANH CONG TOAN BO VAO C:\inetpub\wwwroot!
echo   👉 Bay gio tren IIS Manager ban hay:
echo      1. Bam nut F5 (Refresh) hoac chuot phai vao Default Web Site -> Refresh
echo      2. Se thay ngay file index.html xuat hien!
echo ==================================================================
pause
