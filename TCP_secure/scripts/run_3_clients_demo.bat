@echo off
setlocal
chcp 65001 >nul

cd /d "%~dp0\.."

if not exist "D:\temp" mkdir "D:\temp" 2>nul
set TMP=D:\temp
set TEMP=D:\temp

echo ===================================================
echo [DEMO] KHOI DONG HE THONG 1 SERVER + 3 CLIENTS (JAVA)
echo ===================================================

echo 1. Kiem tra bien dich...
if not exist "server\target\classes\com\example\server\Program.class" (
    call scripts\compile_all.bat
)

echo 2. Khoi dong Server tai port 5000...
start "SecureChat Server [Port 5000]" cmd /k "scripts\run_server.bat"

echo Cho Server khoi dong trong 2 giay...
timeout /t 2 /nobreak >nul

echo 3. Mo Client 1 (An - pass123)...
start "Client 1 - An" cmd /k "java -Djava.io.tmpdir=D:\temp -Dfile.encoding=UTF-8 -cp lib/*;model/target/classes;client/target/classes com.example.client.Program --cli --user an --pass pass123"

echo 4. Mo Client 2 (Binh - pass456)...
start "Client 2 - Binh" cmd /k "java -Djava.io.tmpdir=D:\temp -Dfile.encoding=UTF-8 -cp lib/*;model/target/classes;client/target/classes com.example.client.Program --cli --user binh --pass pass456"

echo 5. Mo Client 3 (Chi - pass789)...
start "Client 3 - Chi" cmd /k "java -Djava.io.tmpdir=D:\temp -Dfile.encoding=UTF-8 -cp lib/*;model/target/classes;client/target/classes com.example.client.Program --cli --user chi --pass pass789"

echo ===================================================
echo [XONG] Da mo 1 cua so Server va 3 cua so Client!
echo Hay quan sat cac client nhan tin nhan broadcast lan nhau.
echo ===================================================
