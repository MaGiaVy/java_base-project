@echo off
chcp 65001 >nul
javac MulticastChat.java ShowNetworkInfo.java NonMember.java
if errorlevel 1 (
  echo.
  echo Compile that bai. Kiem tra java/javac -version.
  pause
  exit /b 1
)
echo Compile thanh cong.
pause
