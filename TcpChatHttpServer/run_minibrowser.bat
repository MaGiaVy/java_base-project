@echo off
chcp 65001 >nul
cd /d "%~dp0"
title MiniBrowser - Kiem thu cheo 4 duong
echo ====================================================================
echo   🌐 KHOI DONG MINIBROWSER (SWING HTTP CLIENT)
echo ====================================================================
echo Dang khoi dong MiniBrowser...
java -Dfile.encoding=UTF-8 MiniBrowser
pause
