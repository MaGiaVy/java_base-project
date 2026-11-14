@echo off
chcp 65001 >nul
set /p NAME=Nhap ten hien thi: 
set /p IP=Nhap IPv4 cua Wi-Fi/Ethernet (bo trong neu muon tu dong): 
if "%IP%"=="" (
  java MulticastChat "%NAME%"
) else (
  java MulticastChat "%NAME%" "%IP%"
)
pause
