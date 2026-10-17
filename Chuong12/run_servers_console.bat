@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ========================================================
echo   KHOI DONG SERVER 1 VA SERVER 2 DANG CONSOLE (SLIDE 13)
echo ========================================================

if not exist bin\server\Server1Console.class (
    echo [*] Dang bien dich ma nguon Java...
    if not exist bin mkdir bin
    javac -encoding UTF-8 -d bin src\server\*.java src\client\*.java
)

echo [*] Dang mo Server 1 (Port 8080) trong cua so moi...
start "HTTP Server 1 (IIS: 8080)" cmd /k "chcp 65001 >nul & cd /d %~dp0 & java -cp bin server.Server1Console"

echo [*] Dang mo Server 2 (Port 8081) trong cua so moi...
start "HTTP Server 2 (App: 8081)" cmd /k "chcp 65001 >nul & cd /d %~dp0 & java -cp bin server.Server2Console"

echo [OK] Ca 2 Server da duoc mo trong 2 cua so Console rieng biet!
timeout /t 3 >nul