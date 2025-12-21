@echo off
setlocal enabledelayedexpansion

REM Root of the repository
set "ROOT=%~dp0"

REM Output folder
set "NFO_OUTPUT=%ROOT%dist-neoforge"

REM Tasks to run for each project
set "TASK=buildAllSupported"

REM NeoForge projects
set NFO_PROJECTS=VMM-neoforge-v1.21-v1.21.1 VMM-neoforge-v1.21.2-v1.21.4 VMM-neoforge-v1.21.5-v1.21.8 VMM-neoforge-v1.21.9-v1.21.10

call :resetDir "%NFO_OUTPUT%"
if errorlevel 1 goto :finish

for %%P in (%NFO_PROJECTS%) do (
    call :buildProject "%%P" "%NFO_OUTPUT%"
    if errorlevel 1 goto :finish
)

echo.
echo NeoForge builds complete: "%NFO_OUTPUT%"
goto :finish

:resetDir
set "DIR=%~1"
if exist "%DIR%" (
    echo Removing previous output: "%DIR%"
    rmdir /s /q "%DIR%" >nul 2>&1
    if exist "%DIR%" (
        echo Previous output is locked; attempting best-effort cleanup...
        del /f /q "%DIR%\\*" >nul 2>&1
        for /d %%D in ("%DIR%\\*") do rmdir /s /q "%%~D" >nul 2>&1
    )
)
if not exist "%DIR%" (
    mkdir "%DIR%" 2>nul
)
if not exist "%DIR%" (
    echo Failed to prepare output directory: "%DIR%"
    exit /b 1
)
echo.>"%DIR%\\.write_test" 2>nul
if not exist "%DIR%\\.write_test" (
    echo Cannot write to output directory: "%DIR%"
    exit /b 1
)
del /q "%DIR%\\.write_test" >nul 2>&1
exit /b 0

:buildProject
set "PROJECT=%~1"
set "DEST=%~2"
echo.
echo === Building %PROJECT% ===
pushd "%ROOT%%PROJECT%" >nul
call gradlew.bat clean %TASK% --no-daemon
set "EXITCODE=%ERRORLEVEL%"
if not "%EXITCODE%"=="0" (
    echo Build failed in %PROJECT%.
    popd >nul
    exit /b %EXITCODE%
)

for %%F in (build\libs\*.jar) do (
    set "JAR=%%~nxF"
    if /I "!JAR:~-12!"=="-sources.jar" (
        echo Skipping sources jar: !JAR!
    ) else (
        echo Copying !JAR!
        copy /y "%%~fF" "%DEST%" >nul
    )
)

popd >nul
exit /b 0

:finish
set "EXIT_CODE=%ERRORLEVEL%"
if not defined VEINMINER_NO_PAUSE pause
exit /b %EXIT_CODE%
