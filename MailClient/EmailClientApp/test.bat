@echo off
call build.bat
if errorlevel 1 exit /b 1
if "%~1"=="" (
  java -Dfile.encoding=UTF-8 -cp "bin;lib/*" org.junit.platform.console.ConsoleLauncher execute --scan-class-path bin --disable-banner
) else (
  java -Dfile.encoding=UTF-8 -cp "bin;lib/*" org.junit.platform.console.ConsoleLauncher execute --select-class %~1 --disable-banner
)
