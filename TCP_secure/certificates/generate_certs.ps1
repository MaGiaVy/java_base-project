# SecureTcpChat: Sinh chung chi TLS bang keytool
$keystorePass = "LabOnly_ChangeMe"
$alias = "server"
$dname = "CN=localhost, OU=Lab, O=SecureChat, L=Hanoi, C=VN"

New-Item -ItemType Directory -Force -Path "server/certificates" | Out-Null
New-Item -ItemType Directory -Force -Path "client/certificates" | Out-Null

$serverP12 = "server/certificates/server.p12"
$serverCrt = "server/certificates/server.crt"
$clientCrt = "client/certificates/server.crt"

if (Test-Path $serverP12) { Remove-Item -Force $serverP12 }
if (Test-Path $serverCrt) { Remove-Item -Force $serverCrt }
if (Test-Path $clientCrt) { Remove-Item -Force $clientCrt }

Write-Host "1. Tao PKCS12 Keystore ($serverP12)..."
& keytool -genkeypair -alias $alias -keyalg RSA -keysize 2048 -keystore $serverP12 -storetype PKCS12 -validity 365 -dname $dname -ext "SAN=DNS:localhost,IP:127.0.0.1" -storepass $keystorePass -keypass $keystorePass

Write-Host "2. Xuat Public Certificate ($serverCrt)..."
& keytool -exportcert -alias $alias -keystore $serverP12 -storetype PKCS12 -file $serverCrt -rfc -storepass $keystorePass

Write-Host "3. Sao chep server.crt sang Client..."
Copy-Item -Force $serverCrt $clientCrt

Write-Host "[HOAN TAT] Sinh chung chi TLS thanh cong!"
