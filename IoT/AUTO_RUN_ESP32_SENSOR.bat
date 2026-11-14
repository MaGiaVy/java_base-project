@echo off
cd /d "%~dp0"
title Tu Dong Phat Du Lieu Cam Bien ESP32 - SGU IoT

echo ========================================================
echo   GIA LAP ESP32 PHAT DU LIEU CAM BIEN TU DONG
echo ========================================================
echo.
echo [INFO] ESP32 dang ket noi Mosquitto Broker tai localhost:1883...
echo [INFO] Cam bien DHT22 se tu dong doc va gui du lieu 2 giay/lan.
echo [INFO] Kim dong ho tren Web Dashboard va App Java se tu dong quay!
echo.
echo Nhap Ctrl + C de dung phat du lieu.
echo ========================================================
echo.

python scripts/mock_esp32_publisher.py --host localhost --port 1883 --user user1 --password 123
pause
