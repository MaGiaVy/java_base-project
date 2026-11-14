@echo off
chcp 65001 >nul
netsh advfirewall firewall add rule name="Java UDP Multicast 8000 In" dir=in action=allow protocol=UDP localport=8000
netsh advfirewall firewall add rule name="Java UDP Multicast 8000 Out" dir=out action=allow protocol=UDP remoteport=8000
echo.
echo Da tao rule firewall cho UDP 8000. Neu bi Access Denied, hay Run as administrator.
pause
