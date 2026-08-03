@echo off
setlocal

set "GRADLE_VERSION=8.14"
set "DIST_NAME=gradle-%GRADLE_VERSION%-bin"
set "DIST_URL=https://services.gradle.org/distributions/%DIST_NAME%.zip"
if defined GRADLE_USER_HOME (
    set "GRADLE_HOME_DIR=%GRADLE_USER_HOME%"
) else if defined USERPROFILE (
    set "GRADLE_HOME_DIR=%USERPROFILE%\.gradle"
) else (
    set "GRADLE_HOME_DIR=%TEMP%\.gradle"
)
set "DIST_ROOT=%GRADLE_HOME_DIR%\wrapper\dists\%DIST_NAME%\portable"
set "ZIP_PATH=%DIST_ROOT%\%DIST_NAME%.zip"
set "GRADLE_DIR=%DIST_ROOT%\gradle-%GRADLE_VERSION%"

if exist "gradle.properties" (
    for /f "usebackq tokens=1,* delims==" %%A in (`type gradle.properties`) do (
        if "%%A"=="org.gradle.java.home" (
            set "JAVA_HOME=%%B"
        )
    )
)

if not exist "%DIST_ROOT%" mkdir "%DIST_ROOT%"

if not exist "%GRADLE_DIR%\bin\gradle.bat" (
    if not exist "%ZIP_PATH%" (
        powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object Net.WebClient).DownloadFile('%DIST_URL%', '%ZIP_PATH%')"
        if errorlevel 1 exit /b 1
    )
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%ZIP_PATH%' -DestinationPath '%DIST_ROOT%' -Force"
    if errorlevel 1 exit /b 1
)

call "%GRADLE_DIR%\bin\gradle.bat" %*
