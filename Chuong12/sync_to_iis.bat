@echo off
chcp 65001 >nul
title DONG BO WEBROOT1 SANG MICROSOFT IIS
echo ========================================================
echo       DONG BO TOAN BO TRANG WEB TU DU AN SANG IIS
echo       Nguon: webroot1  ---^>  Dich: C:\inetpub\wwwroot
echo ========================================================
echo.

:: Kiem tra quyen Administrator, neu chua co thi tu dong xin quyen UAC
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo [THONG BAO] Dang yeu cau quyen Administrator de ghi du lieu vao C:\inetpub\wwwroot...
    powershell -Command "Start-Process cmd -ArgumentList '/c `\"%~dpnx0`\"' -Verb RunAs"
    exit /b
)

echo [1/3] Dang cap quyen ghi cho Users tai C:\inetpub\wwwroot...
icacls "C:\inetpub\wwwroot" /grant Users:(OI)(CI)M /T >nul 2>&1

echo [2/3] Dang sao chep toan bo file (P6.HTM, crawled...) sang IIS...
xcopy "webroot1\*" "C:\inetpub\wwwroot\" /E /Y /I >nul

echo.
echo ========================================================
echo  [HOAN TAT THANH CONG]
echo  - Da sao chep tat ca cac trang web sang C:\inetpub\wwwroot
echo  - Da mo quyen ghi cho C:\inetpub\wwwroot de Java tu dong ghi tiep
echo.
echo  👉 BAY GIO BAN HAY:
echo  1. Mo IIS Manager (inetmgr)
echo  2. Chon Default Web Site
echo  3. Bam phim F5 de lam moi: ban se thay ngay P6.HTM va thu muc crawled!
echo ========================================================
echo.
pause
