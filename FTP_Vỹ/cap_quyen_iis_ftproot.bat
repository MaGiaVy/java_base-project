@echo off
chcp 65001 >nul
title CAP QUYEN GHI CHO THU MUC IIS FTP (C:\inetpub\ftproot)
echo ========================================================
echo       CAP QUYEN GHI CHO THU MUC WINDOWS IIS FTP
echo               C:\inetpub\ftproot
echo ========================================================
echo.

net session >nul 2>&1
if %errorLevel% neq 0 (
    echo [THONG BAO] Dang yeu cau quyen Administrator...
    powershell -Command "Start-Process cmd -ArgumentList '/c `\"%~dpnx0`\"' -Verb RunAs"
    exit /b
)

echo Dang cap quyen Ghi (Modify) cho Users va IUSR tai C:\inetpub\ftproot...
icacls "C:\inetpub\ftproot" /grant Users:(OI)(CI)M /T
icacls "C:\inetpub\ftproot" /grant IUSR:(OI)(CI)M /T

if %errorLevel% equ 0 (
    echo.
    echo ========================================================
    echo  [THANH CONG] Da cap quyen ghi thanh cong cho C:\inetpub\ftproot!
    echo  Bay gio FileZilla va Java Client co the Upload thoai mai len IIS!
    echo ========================================================
) else (
    echo [LOI] Cap quyen that bai!
)
echo.
pause
