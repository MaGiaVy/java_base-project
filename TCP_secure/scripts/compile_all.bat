@echo off
setlocal
echo ===================================================
echo [SecureTcpChat] BIEN DICH TOAN BO MA NGUON JAVA
echo ===================================================

chcp 65001 >nul

if not exist "D:\temp" mkdir "D:\temp" 2>nul
set TMP=D:\temp
set TEMP=D:\temp

echo 1. Tao thu muc target/classes...
if not exist "model\target\classes" mkdir "model\target\classes"
if not exist "server\target\classes" mkdir "server\target\classes"
if not exist "client\target\classes" mkdir "client\target\classes"
if not exist "server\target\test-classes" mkdir "server\target\test-classes"

echo 2. Bien dich model...
javac -encoding UTF-8 -cp "lib/*" -d model/target/classes model/src/main/java/com/example/model/*.java
if %ERRORLEVEL% NEQ 0 (
    echo [LOI] Bien dich model that bai!
    pause
    exit /b %ERRORLEVEL%
)

echo 3. Dong goi model-1.0.0.jar...
jar cvf lib/model-1.0.0.jar -C model/target/classes . >nul

echo 4. Bien dich server...
javac -encoding UTF-8 -cp "lib/*;model/target/classes" -d server/target/classes server/src/main/java/com/example/server/*.java
if %ERRORLEVEL% NEQ 0 (
    echo [LOI] Bien dich server that bai!
    pause
    exit /b %ERRORLEVEL%
)

echo 5. Bien dich client...
javac -encoding UTF-8 -cp "lib/*;model/target/classes" -d client/target/classes client/src/main/java/com/example/client/*.java client/src/main/java/com/example/client/cli/*.java client/src/main/java/com/example/client/ui/*.java
if %ERRORLEVEL% NEQ 0 (
    echo [LOI] Bien dich client that bai!
    pause
    exit /b %ERRORLEVEL%
)

echo 6. Bien dich SecurityTestRunner...
javac -encoding UTF-8 -cp "lib/*;model/target/classes;server/target/classes;client/target/classes" -d server/target/test-classes server/src/test/java/com/example/server/SecurityTestRunner.java
if %ERRORLEVEL% NEQ 0 (
    echo [LOI] Bien dich SecurityTestRunner that bai!
    pause
    exit /b %ERRORLEVEL%
)

echo ===================================================
echo [HOAN TAT] Tat ca cac module da duoc bien dich thanh cong!
echo ===================================================
