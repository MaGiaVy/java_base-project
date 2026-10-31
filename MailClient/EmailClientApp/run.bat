@echo off
call build.bat
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -cp "bin;lib/*" EmailClientApp
