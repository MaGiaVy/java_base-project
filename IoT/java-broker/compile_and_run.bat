@echo off
cd /d "%~dp0"
title IoT Smart Monitoring - Java MQTT Broker

echo ========================================================
echo   COMPILING AND LAUNCHING JAVA MQTT BROKER (TCP 1883 / WS 9001)
echo ========================================================
echo.

:: Thu dung Windows Service mosquitto va mosquitto.exe neu dang chiem cong 1883 / 9001
net stop mosquitto >nul 2>nul
taskkill /f /im mosquitto.exe >nul 2>nul

:: Tat tien trinh Java Broker cu neu dang chay de giai phong cong
powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*com.iot.broker*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }" >nul 2>nul

if not exist "bin" mkdir bin

echo [1/2] Dang bien dich ma nguon Java Broker...
javac -encoding UTF-8 -d bin src/com/iot/broker/MqttBrokerServer.java src/com/iot/broker/BrokerFrame.java
if errorlevel 1 goto COMPILE_ERROR

echo.
echo [2/2] Bien dich thanh cong! Dang khoi chay Java MQTT Broker...
if "%~1"=="--console" goto RUN_CONSOLE

start "IoT Java MQTT Broker" java -cp bin com.iot.broker.BrokerFrame %*
echo.
echo [HOAN TAT] Da mo cua so Java MQTT Broker (TCP: 1883, WS: 9001)!
pause
goto END

:RUN_CONSOLE
java -cp bin com.iot.broker.BrokerFrame %*
goto END

:COMPILE_ERROR
echo.
echo [LOI] Bien dich Java Broker that bai! Kiem tra lai JDK tren may.
pause
exit /b 1

:END
