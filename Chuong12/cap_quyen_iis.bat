@echo off
chcp 65001 >nul
title CAP QUYEN GHI CHO THU MUC IIS (C:\inetpub\wwwroot)
echo ========================================================
echo       CAP QUYEN GHI CHO THU MUC WINDOWS IIS
echo               C:\inetpub\wwwroot
echo ========================================================
echo.

:: Kiem tra quyen Admin
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo [THONG BAO] Dang yeu cau quyen Administrator...
    powershell -Command "Start-Process cmd -ArgumentList '/c `\"%~dpnx0`\"' -Verb RunAs"
    exit /b
)

echo Dang cap quyen Ghi (Modify) cho nhom Users tai C:\inetpub\wwwroot...
icacls "C:\inetpub\wwwroot" /grant Users:(OI)(CI)M /T
if %errorLevel% equ 0 (
    echo.
    echo ========================================================
    echo  [THANH CONG] Da cap quyen ghi thanh cong cho C:\inetpub\wwwroot!
    echo  Bay gio Java Server, Trinh duyet cao web va nut Tao Trang Moi
    echo  se tu dong ghi thang vao IIS ma khong bi chan quyen nua.
    echo ========================================================
) else (
    echo [LOI] Cap quyen that bai!
)
echo.
pause
