@echo off
cd /d "%~dp0"
title IoT Smart Monitoring - Java Client

if not exist "bin" mkdir bin

echo ========================================================
echo   COMPILING AND LAUNCHING JAVA IOT CLIENT
echo ========================================================
echo.
echo [1/2] Dang bien dich ma nguon Java...

javac -encoding UTF-8 -cp "lib/org.eclipse.paho.client.mqttv3-1.2.5.jar" -d bin src/com/iot/MqttManager.java src/com/iot/MainFrame.java src/com/iot/App.java

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [LOI] Bien dich Java that bai! Kiem tra lai JDK tren may.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [2/2] Bien dich thanh cong! Dang khoi chay giao dien...
powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*com.iot.App*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }" >nul 2>nul
start "IoT Java Client" java -cp "bin;lib/org.eclipse.paho.client.mqttv3-1.2.5.jar" com.iot.App

echo.
echo [HOAN TAT] Da khoi chay ung dung Java thanh cong!
pause
