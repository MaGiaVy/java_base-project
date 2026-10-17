@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ========================================================
echo   KHOI DONG HE THONG (SERVER GUI + MINI CHROME)
echo ========================================================

if not exist bin\server\ServerDashboardGUI.class (
    echo [*] Dang bien dich ma nguon Java lan dau...
    if not exist bin mkdir bin
    javac --module-path javafx-sdk-24\lib --add-modules javafx.controls,javafx.swing,javafx.web -encoding UTF-8 -d bin src\crawler\*.java src\server\*.java src\client\*.java
)

echo [*] 1. Dang mo Server Dashboard GUI...
start "Server Dashboard GUI" javaw -cp bin server.ServerDashboardGUI

ping 127.0.0.1 -n 3 >nul

echo [*] 2. Dang mo Mini Chrome Browser (nhan WebKit JavaFX)...
start "Mini Chrome Browser" javaw --module-path javafx-sdk-24\lib --add-modules javafx.controls,javafx.swing,javafx.web --enable-native-access=javafx.graphics,javafx.web -cp bin client.MiniChromeBrowser

echo ========================================================
echo [HOAN TAT] Da khoi dong ca 2 ung dung giao dien:
echo  1. Bang dieu khien Server (Server Dashboard GUI)
echo  2. Trinh duyet Chrome thu nho (Mini Chrome Browser)
echo.
echo Nhan phim bat ky de thoat cua so lenh nay...
echo ========================================================
pause >nul