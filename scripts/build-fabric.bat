@echo off
setlocal enabledelayedexpansion

REM Root of the repository
for %%I in ("%~dp0..") do set "ROOT=%%~fI\"

REM Output folder
set "FAB_OUTPUT=%ROOT%artifacts\dist-fabric"

REM Tasks to run for each project
set "TASK=buildAllSupported"

REM Load project list from shared metadata.
call "%ROOT%config\version-metadata.bat" build
if errorlevel 1 goto :finish
if not defined FAB_PROJECTS (
    echo [error] FAB_PROJECTS was not provided by version-metadata.bat
    goto :finish
)

call :resetDir "%FAB_OUTPUT%"
if errorlevel 1 goto :finish

for %%P in (%FAB_PROJECTS%) do (
    call :buildProject "%%P" "%FAB_OUTPUT%"
    if errorlevel 1 goto :finish
)

echo.
echo Fabric builds complete: "%FAB_OUTPUT%"
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
set "GRADLE_USER_HOME=%ROOT%%PROJECT%\.gradle_local_build"
set "LOCAL_TEMP_DIR=%ROOT%.tmp\%PROJECT%"
if not exist "%LOCAL_TEMP_DIR%" (
    mkdir "%LOCAL_TEMP_DIR%" >nul 2>&1
)
set "TEMP=%LOCAL_TEMP_DIR%"
set "TMP=%LOCAL_TEMP_DIR%"
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
