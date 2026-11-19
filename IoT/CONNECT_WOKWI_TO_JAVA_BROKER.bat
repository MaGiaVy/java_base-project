@echo off
cd /d "%~dp0"
python -u "%~dp0scripts\wokwi_tunnel.py" %*
pause
