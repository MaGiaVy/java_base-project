@echo off
chcp 65001 >nul
set /p NAME=Nhap ten may doi chung: 
java NonMember "%NAME%"
pause
