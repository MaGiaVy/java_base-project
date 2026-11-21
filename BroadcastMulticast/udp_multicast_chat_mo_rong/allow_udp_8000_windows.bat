@echo off
:: Tu dong xin quyen Administrator
fltmc >nul 2>&1
if errorlevel 1 (
    echo Dang yeu cau quyen Administrator...
    powershell -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b
)

chcp 65001 >nul
echo ============================================================
echo   MO TUONG LUA WINDOWS (FIREWALL) CHO UDP MULTICAST & BROADCAST
echo ============================================================
echo.
echo [1/3] Mo cong UDP 8000 (Multicast phong chat)...
netsh advfirewall firewall add rule name="Java UDP Multicast 8000 In" dir=in action=allow protocol=UDP localport=8000 profile=any >nul
netsh advfirewall firewall add rule name="Java UDP Multicast 8000 Out" dir=out action=allow protocol=UDP remoteport=8000 profile=any >nul

echo [2/3] Mo cong UDP 8001 (Broadcast phan mo rong)...
netsh advfirewall firewall add rule name="Java UDP Broadcast 8001 In" dir=in action=allow protocol=UDP localport=8001 profile=any >nul
netsh advfirewall firewall add rule name="Java UDP Broadcast 8001 Out" dir=out action=allow protocol=UDP remoteport=8001 profile=any >nul

echo [3/3] Mo cong UDP 7000 (Broadcast LanChat)...
netsh advfirewall firewall add rule name="Java UDP Broadcast 7000 In" dir=in action=allow protocol=UDP localport=7000 profile=any >nul
netsh advfirewall firewall add rule name="Java UDP Broadcast 7000 Out" dir=out action=allow protocol=UDP remoteport=7000 profile=any >nul

echo.
echo [OK] Da mo xong toan bo cong UDP cho tat ca cac che do mang (Public va Private)!
echo ============================================================
pause
