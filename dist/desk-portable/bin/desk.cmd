@echo off
setlocal EnableExtensions

set "SCRIPT_DIR=%~dp0"
set "RUNTIME_JAVA=%SCRIPT_DIR%..\runtime\bin\java.exe"
set "APP_JAR=%SCRIPT_DIR%..\lib\desk-cli.jar"

if not exist "%RUNTIME_JAVA%" (
    echo Error: runtime Java not found at %RUNTIME_JAVA% >&2
    exit /b 1
)

if not exist "%APP_JAR%" (
    echo Error: application JAR not found at %APP_JAR% >&2
    exit /b 1
)

"%RUNTIME_JAVA%" -jar "%APP_JAR%" %*
