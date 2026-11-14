@echo off
setlocal
chcp 65001 >nul
title SecureChat TLS Server - Port 5000

cd /d "%~dp0\.."

if not exist "D:\temp" mkdir "D:\temp" 2>nul
set TMP=D:\temp
set TEMP=D:\temp

if not exist "server\target\classes\com\example\server\Program.class" (
    echo [THONG BAO] Chua co ma nhi phan server, dang bien dich...
    call scripts\compile_all.bat
)

if not exist "server\certificates\server.p12" (
    echo [THONG BAO] Chua co chung chi server.p12, dang sinh chung chi...
    call certificates\generate_certs.bat
)

echo ===================================================
echo [SecureChat Server] Khoi dong Server TLS tai cong 5000...
echo Keystore: server\certificates\server.p12 (Pass: LabOnly_ChangeMe)
echo ===================================================

java -Djava.io.tmpdir=D:\temp -Dfile.encoding=UTF-8 -cp "lib/*;model/target/classes;server/target/classes" com.example.server.Program 5000

pause
