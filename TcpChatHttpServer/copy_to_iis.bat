@echo off
chcp 65001 >nul
title Sao chep P1.html va P2.html vao C:\inetpub\wwwroot (IIS)
echo ==================================================================
echo   🚀 COPY FILE P1.html & P2.html VAO THU MUC IIS (C:\inetpub\wwwroot)
echo ==================================================================
echo.

:: Kiem tra quyen Administrator de copy vao thu muc he thong C:\inetpub\wwwroot
net session >nul 2>&1
if %errorLevel% == 0 (
    echo [OK] Dang chay voi quyen Administrator!
) else (
    echo [INFO] Yeu cau cap quyen Administrator de ghi file vao C:\inetpub\wwwroot...
    powershell -Command "Start-Process cmd -ArgumentList '/c copy /y \"%~dp0P1.html\" \"C:\inetpub\wwwroot\" & copy /y \"%~dp0P2.html\" \"C:\inetpub\wwwroot\" & copy /y \"%~dp0P1.HTM\" \"C:\inetpub\wwwroot\" & copy /y \"%~dp0P2.HTM\" \"C:\inetpub\wwwroot\" & pause' -Verb RunAs"
    exit /b 0
)

copy /y "%~dp0P1.html" "C:\inetpub\wwwroot"
copy /y "%~dp0P2.html" "C:\inetpub\wwwroot"
copy /y "%~dp0P1.HTM" "C:\inetpub\wwwroot" >nul 2>&1
copy /y "%~dp0P2.HTM" "C:\inetpub\wwwroot" >nul 2>&1

echo.
echo ==================================================================
echo   ✅ DA SAO CHEP THANH CONG VAO C:\inetpub\wwwroot!
echo   👉 Bay gio ban co the kiem thu:
echo      - Duong 1: Mo trinh duyet go http://localhost/P1.html
echo      - Duong 4: Chay run_http_client.bat chon menu 5
echo ==================================================================
pause
