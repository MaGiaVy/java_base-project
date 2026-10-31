@echo off
chcp 65001 >nul
title LAN Chat Runner
cls
echo ================================================================
echo   LAN CHAT - UDP BROADCAST VA MULTICAST (JAVA SWING GUI)
echo ================================================================
echo.

echo [1/2] Dang bien dich ma nguon Java...
javac -encoding UTF-8 LanChat.java LanChatGUI.java
if errorlevel 1 (
    echo.
    echo [LOI] Bien dich that bai! Vui long kiem tra ma nguon.
    echo.
    pause
    exit /b 1
)
echo [OK] Bien dich thanh cong!
echo.

echo [2/2] Dang khoi chay 2 cua so LAN Chat (User_1 va User_2)...
start "LAN Chat 1" java -Dfile.encoding=UTF-8 -Djava.net.preferIPv4Stack=true LanChat "User_1"
ping -n 2 127.0.0.1 >nul
start "LAN Chat 2" java -Dfile.encoding=UTF-8 -Djava.net.preferIPv4Stack=true LanChat "User_2"

echo.
echo ================================================================
echo   Da khoi chay thanh cong 2 cua so LAN Chat!
echo   Ban co the bat dau chat thu nghiem.
echo ================================================================
echo.
pause
