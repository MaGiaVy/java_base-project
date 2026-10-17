@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ========================================================
echo   BIEN DICH DU AN LAP TRINH MANG (HTTP CLIENT - SERVER)
echo ========================================================

if not exist bin mkdir bin

echo [*] Dang bien dich cac file ma nguon Java (tich hop JavaFX WebKit)...
javac --module-path javafx-sdk-24\lib --add-modules javafx.controls,javafx.swing,javafx.web -encoding UTF-8 -d bin src\crawler\*.java src\server\*.java src\client\*.java

if %ERRORLEVEL% equ 0 (
    echo [OK] Bien dich thanh cong! Cac file .class da duoc tao trong thu muc bin\
) else (
    echo [LOI] Qua trinh bien dich that bai. Hay kiem tra lai loi tren man hinh.
)

pause