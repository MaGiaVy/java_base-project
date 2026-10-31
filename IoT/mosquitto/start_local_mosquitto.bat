@echo off
chcp 65001 >nul
echo ========================================================
echo   KHỞI ĐỘNG MOSQUITTO MQTT BROKER (TCP 1883 & WS 9001)
echo ========================================================
echo.

set CONFIG_FILE=%~dp0mosquitto.conf

where mosquitto >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo [INFO] Tìm thấy mosquitto trong biến môi trường PATH.
    echo [INFO] Đang chạy: mosquitto -c "%CONFIG_FILE%" -v
    mosquitto -c "%CONFIG_FILE%" -v
    goto END
)

if exist "C:\Program Files\mosquitto\mosquitto.exe" (
    echo [INFO] Tìm thấy mosquitto tại C:\Program Files\mosquitto
    "C:\Program Files\mosquitto\mosquitto.exe" -c "%CONFIG_FILE%" -v
    goto END
)

if exist "D:\mosquitto\mosquitto.exe" (
    echo [INFO] Tìm thấy mosquitto tại D:\mosquitto
    "D:\mosquitto\mosquitto.exe" -c "%CONFIG_FILE%" -v
    goto END
)

echo [CHÚ Ý] Không tìm thấy mosquitto.exe trên máy tính!
echo Bạn có thể:
echo   1. Tải và giải nén Mosquitto vào D:\mosquitto theo hướng dẫn trong file 2.TH.docx.
echo   2. HOẶC sử dụng cấu hình dự phòng 'public_fallback' (broker.emqx.io) để chạy ngay lập tức mà không cần cài đặt.
echo.
pause

:END
