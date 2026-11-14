@echo off
setlocal
chcp 65001 >nul
title SecureChat Client (GUI)

cd /d "%~dp0\.."

if not exist "D:\temp" mkdir "D:\temp" 2>nul
set TMP=D:\temp
set TEMP=D:\temp

if not exist "client\target\classes\com\example\client\Program.class" (
    echo [THONG BAO] Chua co ma nhi phan client, dang bien dich...
    call scripts\compile_all.bat
)

echo ===================================================
echo [SecureChat Client GUI] Khoi chay giao dien do hoa Swing...
echo ===================================================

java -Djava.io.tmpdir=D:\temp -Dfile.encoding=UTF-8 -cp "lib/*;model/target/classes;client/target/classes" com.example.client.Program %*
