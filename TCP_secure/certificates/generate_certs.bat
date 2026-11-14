@echo off
setlocal
echo ===================================================
echo [SecureTcpChat] TAO CHUNG CHI TLS & KEYSTORE (JAVA)
echo ===================================================

set KEYSTORE_PASS=LabOnly_ChangeMe
set KEY_ALIAS=server
set DNAME=CN=localhost, OU=Lab, O=SecureChat, L=Hanoi, C=VN

mkdir server\certificates 2>nul
mkdir client\certificates 2>nul

del /q server\certificates\server.p12 2>nul
del /q server\certificates\server.crt 2>nul
del /q client\certificates\server.crt 2>nul

echo 1. Tao PKCS12 Keystore (server.p12) chua Private Key va Public Certificate...
keytool -genkeypair -alias %KEY_ALIAS% -keyalg RSA -keysize 2048 -keystore server\certificates\server.p12 -storetype PKCS12 -validity 365 -dname "%DNAME%" -ext "SAN=DNS:localhost,IP:127.0.0.1" -storepass %KEYSTORE_PASS% -keypass %KEYSTORE_PASS%

if %ERRORLEVEL% NEQ 0 (
    echo [LOI] Khong the tao keystore bang keytool!
    exit /b %ERRORLEVEL%
)

echo 2. Xuat Public Certificate (server.crt)...
keytool -exportcert -alias %KEY_ALIAS% -keystore server\certificates\server.p12 -storetype PKCS12 -file server\certificates\server.crt -rfc -storepass %KEYSTORE_PASS%

if %ERRORLEVEL% NEQ 0 (
    echo [LOI] Khong the xuat server.crt!
    exit /b %ERRORLEVEL%
)

echo 3. Sao chep server.crt sang thu muc Client (Luu y: KHONG sao chep server.p12)...
copy /y server\certificates\server.crt client\certificates\server.crt >nul

echo ===================================================
echo [HOAN TAT] Chung chi TLS da duoc tao thanh cong!
echo Server Keystore: server\certificates\server.p12
echo Server Public Cert: server\certificates\server.crt
echo Client Trusted Cert: client\certificates\server.crt
echo ===================================================
