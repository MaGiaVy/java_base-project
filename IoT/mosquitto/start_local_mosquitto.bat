@echo off
cd /d "%~dp0"
title Mosquitto MQTT Broker Local

echo ========================================================
echo   KHOI DONG MOSQUITTO MQTT BROKER (LOCAL)
echo ========================================================
echo.

:: Thu tat service chay ngam cua Windows (neu co)
net stop mosquitto >nul 2>nul

set "CONFIG_FILE=%~dp0mosquitto.conf"

:: 1. Kiem tra C:\Program Files\mosquitto
if exist "C:\Program Files\mosquitto\mosquitto.exe" (
    echo [INFO] Tim thay mosquitto tai C:\Program Files\mosquitto
    echo [INFO] Dang khoi chay Broker tai Port 1883 TCP va Port 9001 WebSockets...
    echo.
    "C:\Program Files\mosquitto\mosquitto.exe" -c "%CONFIG_FILE%" -v
    goto END
)

:: 2. Kiem tra trong PATH
where mosquitto >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo [INFO] Tim thay mosquitto trong PATH.
    echo [INFO] Dang khoi chay Broker tai Port 1883 TCP va Port 9001 WebSockets...
    echo.
    mosquitto -c "%CONFIG_FILE%" -v
    goto END
)

:: 3. Kiem tra D:\mosquitto
if exist "D:\mosquitto\mosquitto.exe" (
    echo [INFO] Tim thay mosquitto tai D:\mosquitto
    echo [INFO] Dang khoi chay Broker tai Port 1883 TCP va Port 9001 WebSockets...
    echo.
    "D:\mosquitto\mosquitto.exe" -c "%CONFIG_FILE%" -v
    goto END
)

echo [LOI] Khong tim thay mosquitto.exe tren may!
pause

:END
pause
