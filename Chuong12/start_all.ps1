# Kịch bản khởi động hệ thống trên PowerShell
Set-Location -LiteralPath $PSScriptRoot

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  KHOI DONG HE THONG (SERVER GUI + MINI CHROME)" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

if (-not (Test-Path "bin\server\ServerDashboardGUI.class")) {
    Write-Host "[*] Dang bien dich ma nguon..." -ForegroundColor Yellow
    if (-not (Test-Path "bin")) { New-Item -ItemType Directory -Path "bin" | Out-Null }
    javac --module-path javafx-sdk-24\lib --add-modules javafx.controls,javafx.swing,javafx.web -encoding UTF-8 -d bin (Get-ChildItem -Recurse -Filter *.java src | ForEach-Object { $_.FullName })
}

Write-Host "[*] 1. Dang mo Server Dashboard GUI..." -ForegroundColor Green
Start-Process -FilePath "javaw" -ArgumentList "-cp", "bin", "server.ServerDashboardGUI"

Start-Sleep -Seconds 2

Write-Host "[*] 2. Dang mo Mini Chrome Browser (nhan WebKit JavaFX)..." -ForegroundColor Green
Start-Process -FilePath "javaw" -ArgumentList "--module-path", "javafx-sdk-24\lib", "--add-modules", "javafx.controls,javafx.swing,javafx.web", "--enable-native-access=javafx.graphics,javafx.web", "-cp", "bin", "client.MiniChromeBrowser"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "[HOAN TAT] Da khoi dong ca 2 ung dung giao dien!" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Cyan