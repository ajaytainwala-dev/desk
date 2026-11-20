@rem ----------------------------------------------------------------------------
@rem Maven Wrapper - a batch script to run Maven without requiring a local
@rem Maven installation. It downloads and runs the Maven distribution declared
@rem in .mvn/maven-wrapper.properties.
@rem ----------------------------------------------------------------------------

@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem Resolve the directory of this script. %~dp0 includes a trailing backslash;
rem strip it so we don't produce double backslashes (\\.) which Windows cannot
rem resolve in the middle of a path.
set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

set "WRAPPER_JAR=%SCRIPT_DIR%\.mvn\maven-wrapper.jar"
set "PROPERTIES_FILE=%SCRIPT_DIR%\.mvn\maven-wrapper.properties"

if not exist %PROPERTIES_FILE% (
    echo Error: Maven Wrapper properties not found at %PROPERTIES_FILE% >&2
    exit /b 1
)

if not exist %WRAPPER_JAR% (
    for /f "tokens=1,* delims==" %%a in ('findstr /b "wrapperUrl=" "%PROPERTIES_FILE%"') do set "WRAPPER_URL=%%b"
    if not defined WRAPPER_URL (
        echo Error: wrapperUrl missing in %PROPERTIES_FILE% >&2
        exit /b 1
    )
    echo Downloading Maven Wrapper from !WRAPPER_URL! >&2
    powershell -NoProfile -Command "Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'"
    if errorlevel 1 (
        echo Error: failed to download Maven Wrapper jar >&2
        exit /b 1
    )
)

rem The maven-wrapper.jar does not declare a Main-Class in its manifest on all
rem builds, so invoke the entry point explicitly and pass the project directory.
java %MAVEN_OPTS% -Dmaven.multiModuleProjectDirectory="%SCRIPT_DIR%" -cp %WRAPPER_JAR% org.apache.maven.wrapper.MavenWrapperMain %*