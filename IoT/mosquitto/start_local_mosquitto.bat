@echo off
cd /d "%~dp0"
title Mosquitto MQTT Broker

echo ========================================================
echo   KHOI DONG MOSQUITTO MQTT BROKER (LOCAL)
echo ========================================================
echo.

set "CONFIG_FILE=%~dp0mosquitto.conf"

:: 1. Kiem tra trong PATH
where mosquitto >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo [INFO] Tim thay mosquitto trong PATH.
    echo [INFO] Dang chay mosquitto tai port 1883 va 9001...
    mosquitto -c "%CONFIG_FILE%" -v
    goto END
)

:: 2. Kiem tra C:\Program Files\mosquitto
if exist "C:\Program Files\mosquitto\mosquitto.exe" (
    echo [INFO] Tim thay mosquitto tai C:\Program Files\mosquitto
    echo [INFO] Dang chay mosquitto tai port 1883 va 9001...
    "C:\Program Files\mosquitto\mosquitto.exe" -c "%CONFIG_FILE%" -v
    goto END
)

:: 3. Kiem tra D:\mosquitto
if exist "D:\mosquitto\mosquitto.exe" (
    echo [INFO] Tim thay mosquitto tai D:\mosquitto
    echo [INFO] Dang chay mosquitto tai port 1883 va 9001...
    "D:\mosquitto\mosquitto.exe" -c "%CONFIG_FILE%" -v
    goto END
)

:: 4. Neu khong tim thay
echo ========================================================
echo [THONG BAO] May tinh cua ban CHUA CO Mosquitto Broker!
echo ========================================================
echo.
echo GIAI PHAP CHO BAN:
echo.
echo 1. Nhanh nhat - Khuyen dung:
echo    Dung CACH 1 [Public Broker: broker.emqx.io].
echo    Ban khong can cai Mosquitto, Wokwi va Web van chay duoc 100%%.
echo.
echo 2. Neu muon cai Mosquitto tren may:
echo    Giai nen mosquitto.rar vao thu muc: D:\mosquitto
echo    (Hoac tai installer tai: https://mosquitto.org/download/)
echo.
echo ========================================================
pause

:END
