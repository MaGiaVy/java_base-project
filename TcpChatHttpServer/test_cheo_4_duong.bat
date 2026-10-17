@echo off
chcp 65001 >nul
cd /d "%~dp0"
title TEST CHEO 4 DUONG - HTTP WEB SERVER & CLIENT

echo ====================================================================
echo   🎯 KICH BAN KIEM THU CHEO 4 DUONG CUA THAY (CHUONG 11 & 12)
echo ====================================================================
echo.
echo   [SERVER 1] Microsoft IIS (Port 80)     -> File index.html, P1.html, P2.html
echo   [SERVER 2] Java Server (Port 8080)     -> File index.html, P3.html, P4.html
echo   [CLIENT 1] Trinh duyet chuan (Browser) -> Chrome / Edge / Firefox
echo   [CLIENT 2] MiniBrowser (Java Client)   -> MiniBrowser.java
echo.
echo --------------------------------------------------------------------
echo   Duong 1: Browser       <---> IIS Server        (http://localhost/P1.html)
echo   Duong 2: Browser       <---> Java Server       (http://localhost:8080/P3.html)
echo   Duong 3: MiniBrowser   <---> Java Server       (http://localhost:8080/P3.html)
echo   Duong 4: MiniBrowser   <---> IIS Server        (http://localhost/P1.html)
echo --------------------------------------------------------------------
echo.

:: 1. Khoi dong Java Server o cua so moi (co cd /d dung thu muc)
echo [1/3] Dang khoi dong Java HTTP Server (Port 8080)...
start "Java HTTP Server (Port 8080)" cmd /k "cd /d \"%~dp0\" && chcp 65001 >nul && java -Dfile.encoding=UTF-8 ChatAndWebServer"

:: Cho 3 giay de Java Server san sang mo Port 8080
echo Dang cho Java Server san sang...
timeout /t 3 >nul

:: 2. Mo Web Portal tren Browser chuan (co the test truc tiep ca 4 duong tren web)
echo [2/3] Dang mo Web Portal va trang kiem thu tren trinh duyet...
start http://localhost/index.html
timeout /t 1 >nul
start http://localhost:8080/P3.html
timeout /t 1 >nul

:: 3. Khoi dong MiniBrowser de test Duong 3 va Duong 4
echo [3/3] Dang mo MiniBrowser de test Duong 3 va Duong 4...
start "MiniBrowser" javaw -Dfile.encoding=UTF-8 MiniBrowser

echo.
echo ====================================================================
echo   ✅ DA KHOI DONG TAT CA CAC THANH PHAN!
echo   👉 Tren Web Portal (Trinh duyet Chrome/Edge):
echo      - Co san so do kien truc, giai thich yeu cau cua Thay!
echo      - Co bo dieu khien test 4 duong 1-click & bang test status code!
echo   👉 Tren MiniBrowser (Cua so ung dung Java):
echo      - Bam nut [Duong 3: Java Server (P3.html)] de test Duong 3!
echo      - Bam nut [Duong 4: IIS (P1.html)] de test Duong 4!
echo      - Bam cac link trong trang de chuyen trang qua lai!
echo ====================================================================
echo.
pause
