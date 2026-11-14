@echo off
setlocal
chcp 65001 >nul
title SecureChat - Bo Kiem Thu Tu Dong 6 Test Cases Bao Mat (Slide 35)

cd /d "%~dp0\.."

if not exist "D:\temp" mkdir "D:\temp" 2>nul
set TMP=D:\temp
set TEMP=D:\temp

echo =========================================================================================
echo  [KIEM THU] CHAY BO KIEM THU TU DONG BAO MAT (TC01 - TC06) THEO DUNG SLIDE 35
echo =========================================================================================

if not exist "server\target\test-classes\com\example\server\SecurityTestRunner.class" (
    call scripts\compile_all.bat
)

echo Dang thuc thi bo kiem thu tu dong...
java -Djava.io.tmpdir=D:\temp -Dfile.encoding=UTF-8 -cp "lib/*;model/target/classes;server/target/classes;client/target/classes;server/target/test-classes" com.example.server.SecurityTestRunner

pause
