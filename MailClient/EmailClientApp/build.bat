@echo off
setlocal enabledelayedexpansion
if not exist bin mkdir bin

set "SRC_FILES="
if exist src\*.java (
    set "SRC_FILES=src\*.java"
)
set "TEST_FILES="
if exist test\*.java (
    set "TEST_FILES=test\*.java"
)

if defined SRC_FILES (
    if defined TEST_FILES (
        javac -encoding UTF-8 -cp ".;lib/*" -d bin src\*.java test\*.java
    ) else (
        javac -encoding UTF-8 -cp ".;lib/*" -d bin src\*.java
    )
) else (
    if defined TEST_FILES (
        javac -encoding UTF-8 -cp ".;lib/*" -d bin test\*.java
    )
)
