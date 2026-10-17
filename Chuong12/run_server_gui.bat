@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ========================================================
echo   KHOI DONG BANG DIEU KHIEN SERVER GUI (DO - TRANG)
echo ========================================================

if not exist bin\server\ServerDashboardGUI.class (
    echo [*] Thu muc bin chua co file class. Dang tu dong bien dich...
    if not exist bin mkdir bin
    javac -encoding UTF-8 -d bin src\server\*.java src\client\*.java
)

echo [*] Dang khoi dong Server Dashboard GUI...
start "Server Dashboard GUI" javaw -cp bin server.ServerDashboardGUI
echo [OK] Da mo giao dien Bang dieu khien Server GUI!