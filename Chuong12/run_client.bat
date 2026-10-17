@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ========================================================
echo   KHOI DONG "CHROME THU NHO" (MINI CHROME CLIENT GUI)
echo ========================================================

if not exist bin\client\MiniChromeBrowser.class (
    echo [*] Thu muc bin chua co file class. Dang tu dong bien dich...
    if not exist bin mkdir bin
    javac --module-path javafx-sdk-24\lib --add-modules javafx.controls,javafx.swing,javafx.web -encoding UTF-8 -d bin src\crawler\*.java src\server\*.java src\client\*.java
)

echo [*] Dang mo trinh duyet Mini Chrome (nhan WebKit JavaFX)...
start "Mini Chrome Browser" javaw --module-path javafx-sdk-24\lib --add-modules javafx.controls,javafx.swing,javafx.web --enable-native-access=javafx.graphics,javafx.web -cp bin client.MiniChromeBrowser
echo [OK] Da mo trinh duyet Chrome thu nho thanh cong!