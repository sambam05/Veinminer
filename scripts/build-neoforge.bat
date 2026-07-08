@echo off
setlocal enabledelayedexpansion

REM Root of the repository
for %%I in ("%~dp0..") do set "ROOT=%%~fI\"

REM Output folder
set "NFO_OUTPUT=%ROOT%artifacts\dist-neoforge"

REM Tasks to run for each project
set "TASK=buildAllSupported"
if not defined NFO_BUILD_TIMEOUT_SECONDS set "NFO_BUILD_TIMEOUT_SECONDS=1200"

REM Load project list from shared metadata.
call "%ROOT%config\version-metadata.bat" build
if errorlevel 1 goto :finish
if defined NFO_PROJECTS_OVERRIDE (
    set "NFO_PROJECTS=%NFO_PROJECTS_OVERRIDE%"
)
if not defined NFO_PROJECTS (
    echo [error] NFO_PROJECTS was not provided by version-metadata.bat
    goto :finish
)

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
set "RETRIED_NEOFORM_FIX=0"
set "NEEDS_NEOFORM_SANITIZE=0"
if /I "%PROJECT%"=="VMM-neoforge-v1.21.9-v1.21.10" set "NEEDS_NEOFORM_SANITIZE=1"
if /I "%PROJECT%"=="VMM-neoforge-v1.21.11" set "NEEDS_NEOFORM_SANITIZE=1"
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
if "%NEEDS_NEOFORM_SANITIZE%"=="1" (
    echo Using local Gradle cache with NeoForge archive sanitizer: "%GRADLE_USER_HOME%"
    call :sanitizeNeoformArtifacts
)
:runBuild
set "GRADLE_ARGS_FOR_TIMEOUT=clean %TASK% --no-daemon"
call :runGradleWithTimeout
set "EXITCODE=%ERRORLEVEL%"
if not "%EXITCODE%"=="0" (
    if "%NEEDS_NEOFORM_SANITIZE%"=="1" if "%RETRIED_NEOFORM_FIX%"=="0" (
        echo Detected failure in %PROJECT%. Re-sanitizing NeoForge archives and retrying once...
        call :sanitizeNeoformArtifacts
        call :clearNeoformRuntimeCache
        set "RETRIED_NEOFORM_FIX=1"
        goto :runBuild
    )
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

:runGradleWithTimeout
echo Running Gradle with timeout %NFO_BUILD_TIMEOUT_SECONDS%s: gradlew.bat %GRADLE_ARGS_FOR_TIMEOUT%
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference = 'Stop';" ^
  "$timeout = [int]$env:NFO_BUILD_TIMEOUT_SECONDS;" ^
  "$argsLine = $env:GRADLE_ARGS_FOR_TIMEOUT;" ^
  "$process = Start-Process -FilePath 'cmd.exe' -ArgumentList @('/d', '/c', 'gradlew.bat ' + $argsLine) -NoNewWindow -PassThru;" ^
  "if (-not $process.WaitForExit($timeout * 1000)) {" ^
  "  Write-Error ('Gradle timed out after ' + $timeout + ' seconds. Killing process tree for PID ' + $process.Id + '.');" ^
  "  & taskkill.exe /PID $process.Id /T /F | Write-Output;" ^
  "  exit 124;" ^
  "}" ^
  "exit $process.ExitCode;"
exit /b %ERRORLEVEL%

:sanitizeNeoformArtifacts
if not defined GRADLE_USER_HOME exit /b 0
if not exist "%GRADLE_USER_HOME%\caches\modules-2\files-2.1\net.neoforged" exit /b 0
echo Sanitizing NeoForge 1.21.9/1.21.10/1.21.11 archives under "%GRADLE_USER_HOME%"...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference = 'Stop';" ^
  "Add-Type -AssemblyName System.IO.Compression.FileSystem;" ^
  "$moduleRoot = Join-Path $env:GRADLE_USER_HOME 'caches\modules-2\files-2.1\net.neoforged';" ^
  "$targets = @();" ^
  "$neoformRoot = Join-Path $moduleRoot 'neoform';" ^
  "if (Test-Path $neoformRoot) {" ^
  "  Get-ChildItem -Path $neoformRoot -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -like '1.21.9-*' -or $_.Name -like '1.21.10-*' -or $_.Name -like '1.21.11-*' } | ForEach-Object {" ^
  "    $targets += Get-ChildItem -Path $_.FullName -Recurse -File -Filter 'neoform-*.zip' -ErrorAction SilentlyContinue;" ^
  "  }" ^
  "}" ^
  "$neoforgeRoot = Join-Path $moduleRoot 'neoforge';" ^
  "if (Test-Path $neoforgeRoot) {" ^
  "  Get-ChildItem -Path $neoforgeRoot -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -like '21.9*' -or $_.Name -like '21.10*' -or $_.Name -like '21.11*' } | ForEach-Object {" ^
  "    $targets += Get-ChildItem -Path $_.FullName -Recurse -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -like 'neoforge-*-sources.jar' -or $_.Name -like 'neoforge-*-universal.jar' };" ^
  "  }" ^
  "}" ^
  "$targets = $targets | Select-Object -ExpandProperty FullName -Unique;" ^
  "if (-not $targets) { Write-Output 'No matching NeoForm/NeoForge archives found.'; exit 0 };" ^
  "$patched = 0; $checked = 0; $deleted = 0;" ^
  "foreach ($src in $targets) {" ^
  "  try {" ^
  "    $checked++;" ^
  "    $tmp = $src + '.normalized'; if (Test-Path $tmp) { Remove-Item $tmp -Force };" ^
  "    $inZip = [System.IO.Compression.ZipFile]::OpenRead($src);" ^
  "    $outZip = [System.IO.Compression.ZipFile]::Open($tmp, [System.IO.Compression.ZipArchiveMode]::Create);" ^
  "    try {" ^
  "      foreach ($entry in $inZip.Entries) {" ^
  "        if ($entry.FullName.EndsWith('/')) { continue };" ^
  "        $newEntry = $outZip.CreateEntry($entry.FullName, [System.IO.Compression.CompressionLevel]::Optimal);" ^
  "        $newEntry.LastWriteTime = $entry.LastWriteTime;" ^
  "        $inStream = $entry.Open(); $outStream = $newEntry.Open();" ^
  "        try { $inStream.CopyTo($outStream) } finally { $outStream.Dispose(); $inStream.Dispose() };" ^
  "      }" ^
  "    } finally { $outZip.Dispose(); $inZip.Dispose() };" ^
  "    $bak = $src + '.bak'; if (-not (Test-Path $bak)) { Copy-Item $src $bak -Force };" ^
  "    Move-Item -Force $tmp $src;" ^
  "    $patched++;" ^
  "    Write-Output ('Sanitized: ' + $src);" ^
  "  } catch {" ^
  "    Write-Output ('Sanitizer failed for: ' + $src + ' (' + $_.Exception.Message + ')');" ^
  "    try { if (Test-Path ($src + '.normalized')) { Remove-Item ($src + '.normalized') -Force } } catch {};" ^
  "    try { Remove-Item $src -Force; $deleted++; Write-Output ('Deleted bad archive to force re-download: ' + $src) } catch { Write-Output ('Could not delete bad archive: ' + $src + ' (' + $_.Exception.Message + ')') };" ^
  "  }" ^
  "}" ^
  "Write-Output ('NeoForge archive sanitizer checked ' + $checked + ' file(s), patched ' + $patched + ', deleted ' + $deleted + '.');"
exit /b 0

:clearNeoformRuntimeCache
if defined GRADLE_USER_HOME if exist "%GRADLE_USER_HOME%\caches\neoformruntime" (
    echo Clearing NeoForm runtime cache: "%GRADLE_USER_HOME%\caches\neoformruntime"
    rmdir /s /q "%GRADLE_USER_HOME%\caches\neoformruntime" >nul 2>&1
)
if exist "build\tmp\neoformruntime" (
    rmdir /s /q "build\tmp\neoformruntime" >nul 2>&1
)
exit /b 0

:finish
set "EXIT_CODE=%ERRORLEVEL%"
if not defined VEINMINER_NO_PAUSE pause
exit /b %EXIT_CODE%
