@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM === Configuration ===
REM Required values (set in environment before calling):
REM   MODRINTH_TOKEN
REM   MODRINTH_PROJECT_ID
REM Optional environment variables:
REM   CHANGELOG_FILE=
REM   MODRINTH_VERSION_TYPE=release
REM   MODRINTH_FEATURED        - true | false             (default: false)
REM   DRY_RUN                  - if set, skip HTTP calls and only log what would upload (default: enabled)
REM   UPLOAD_LIMIT             - stop after N uploads (blank for no limit)
REM   TARGET_UPLOADS           - fabric | neoforge | both (prompted when missing)
REM
REM The script scans dist-neoforge and dist-fabric for *.jar, parses the name
REM Veinminer-<loader>-<modVersion>+mc<mcVersion>.jar, then posts each file to
REM Modrinth using the provided token and changelog.

REM Pre-set release defaults (non-secret)
if not defined MODRINTH_PROJECT_ID set "MODRINTH_PROJECT_ID=MnavVAzj"
if not defined MODRINTH_VERSION_TYPE set "MODRINTH_VERSION_TYPE=beta"
if not defined DRY_RUN set "DRY_RUN="
if not defined KEEP_JSON set "KEEP_JSON=1"
if not defined CHANGELOG_FILE set "CHANGELOG_FILE=C:\Programming\VMM Rebuild\changelog.md"
if not defined UPLOAD_LIMIT set "UPLOAD_LIMIT="
set "TARGET_UPLOADS=both"

set "ROOT=%~dp0"
if "%CHANGELOG_FILE%"=="" set "CHANGELOG_FILE=%ROOT%changelog.md"
if "%MODRINTH_VERSION_TYPE%"=="" set "MODRINTH_VERSION_TYPE=release"
if "%MODRINTH_FEATURED%"=="" set "MODRINTH_FEATURED=false"

REM Load supported upload versions from shared metadata.
call "%ROOT%version-metadata.bat" upload
if errorlevel 1 goto :finish
if not defined NEO_VERSIONS (
    echo [error] NEO_VERSIONS was not provided by version-metadata.bat
    goto :finish
)
if not defined FAB_VERSIONS (
    echo [error] FAB_VERSIONS was not provided by version-metadata.bat
    goto :finish
)

where curl >nul 2>nul
if errorlevel 1 (
    echo [error] curl is not available in PATH.
    goto :finish
)

call :requireEnv MODRINTH_TOKEN
call :requireEnv MODRINTH_PROJECT_ID
if defined MISSING_ENV goto :finish

if not exist "%CHANGELOG_FILE%" (
    echo [error] Changelog file not found: "%CHANGELOG_FILE%"
    goto :finish
)

call :readChangelog "%CHANGELOG_FILE%"
if errorlevel 1 goto :finish
if not defined CHANGELOG_JSON (
    echo [error] Changelog is empty after reading "%CHANGELOG_FILE%".
    goto :finish
)
if "!CHANGELOG_JSON!"=="" (
    echo [error] Changelog is empty after reading "%CHANGELOG_FILE%".
    goto :finish
)

if defined UPLOAD_LIMIT (
    echo !UPLOAD_LIMIT!| findstr /R "^[0-9][0-9]*$" >nul
    if errorlevel 1 (
        echo [error] UPLOAD_LIMIT must be a positive integer.
        goto :finish
    )
    if !UPLOAD_LIMIT! LEQ 0 (
        echo [error] UPLOAD_LIMIT must be greater than zero.
        goto :finish
    )
)

set "UPLOADED=0"
set "STOP_UPLOADS="

call :chooseTargets
if errorlevel 1 goto :finish

echo Using changelog from "%CHANGELOG_FILE%"
if defined UPLOAD_LIMIT (
    if "!UPLOAD_LIMIT!"=="" (
        echo [info] Upload limit: unlimited
    ) else (
        echo [info] Upload limit: !UPLOAD_LIMIT!
    )
)
echo.
if defined PROCESS_NEOFORGE (
    call :processList dist-neoforge "!NEO_VERSIONS!"
    if errorlevel 1 goto :finish
    if defined STOP_UPLOADS goto :finish
)
if defined PROCESS_FABRIC (
    call :processList dist-fabric "!FAB_VERSIONS!"
    if errorlevel 1 goto :finish
    if defined STOP_UPLOADS goto :finish
)

goto :finish

:chooseTargets
if not defined TARGET_UPLOADS (
    echo Choose upload target: [F]abric, [N]eoforge, [B]oth ^(default=B^)
    set "TARGET_UPLOADS=both"
    set /p "TARGET_UPLOADS=> "
    if "!TARGET_UPLOADS!"=="" set "TARGET_UPLOADS=both"
)
for /f "tokens=1" %%T in ("!TARGET_UPLOADS!") do set "TARGET_CHOICE=%%~T"
if /I "!TARGET_CHOICE!"=="f" set "TARGET_CHOICE=fabric"
if /I "!TARGET_CHOICE!"=="n" set "TARGET_CHOICE=neoforge"
if /I "!TARGET_CHOICE!"=="b" set "TARGET_CHOICE=both"
set "PROCESS_FABRIC="
set "PROCESS_NEOFORGE="
if /I "!TARGET_CHOICE!"=="fabric" set "PROCESS_FABRIC=1"
if /I "!TARGET_CHOICE!"=="neoforge" set "PROCESS_NEOFORGE=1"
if /I "!TARGET_CHOICE!"=="both" (
    set "PROCESS_FABRIC=1"
    set "PROCESS_NEOFORGE=1"
)
if not defined PROCESS_FABRIC if not defined PROCESS_NEOFORGE (
    echo [error] TARGET_UPLOADS must be fabric, neoforge, or both.
    exit /b 1
)
echo [info] Upload target: !TARGET_CHOICE!
exit /b 0

:processList
set "DIST_PATH=%ROOT%%~1"
set "VERSION_LIST=%~2"
echo [info] Scanning !DIST_PATH!
if not exist "!DIST_PATH!" (
    echo [warn] Skipping missing folder: !DIST_PATH!
    exit /b 0
)
for %%V in (!VERSION_LIST!) do (
    set "MATCH=!DIST_PATH!\*+mc%%V.jar"
    set "FOUND="
    for %%F in ("!MATCH!") do (
        if exist "%%~fF" (
            set "FOUND=1"
            echo [info] Queued %%~nxF
            call :checkLimit
            if defined STOP_UPLOADS exit /b 0
            call :processJar "%%~fF"
            if errorlevel 1 exit /b 1
            set /a UPLOADED+=1
            call :checkLimit
            if defined STOP_UPLOADS exit /b 0
        )
    )
    if not defined FOUND echo [warn] Missing version %%V in !DIST_PATH!
)
exit /b 0

:processJar
set "FILE=%~1"
set "BASENAME=%~n1"

REM Expect Veinminer-<loader>-<modVersion>+mc<mcVersion>
for /f "tokens=1-4 delims=-+" %%A in ("%BASENAME%") do (
    set "MOD_NAME=%%A"
    set "LOADER=%%B"
    set "MOD_VERSION=%%C"
    set "MC_TAG=%%D"
)

if not defined MC_TAG (
    echo [error] Could not parse MC version from "%BASENAME%". Expected +mc<version> suffix.
    exit /b 1
)

set "MC_VERSION=!MC_TAG:mc=!"
set "VERSION_NUMBER=!MOD_VERSION!+!MC_VERSION!"
set "DISPLAY_NAME=!MOD_NAME! !MC_VERSION! (!LOADER!)"

echo === Publishing "!FILE!" ===
echo [info] Request JSON will be written to: %TEMP%\mr_data_*.json
call :uploadModrinth
if errorlevel 1 exit /b 1
exit /b 0

:uploadModrinth
set "MR_DATA=%TEMP%\mr_data_%RANDOM%.json"
set "CHANGELOG_ESC=!CHANGELOG_JSON!"
set "CHANGELOG_ESC=!CHANGELOG_ESC:\=\\!"
set "CHANGELOG_ESC=!CHANGELOG_ESC:"=\\\"!"
set "CHANGELOG_ESC=!CHANGELOG_ESC:\\n=\n!"
> "!MR_DATA!" (
    echo {
    echo   "project_id":"%MODRINTH_PROJECT_ID%",
    echo   "name":"!DISPLAY_NAME!",
    echo   "version_number":"!VERSION_NUMBER!",
    echo   "changelog":"!CHANGELOG_ESC!",
    echo   "game_versions":["!MC_VERSION!"],
    echo   "version_type":"%MODRINTH_VERSION_TYPE%",
    echo   "loaders":["!LOADER!"],
    echo   "featured":%MODRINTH_FEATURED%,
    echo   "dependencies":[],
    echo   "file_parts":["file"]
    echo }
)
if defined DRY_RUN (
    echo [dry-run] Modrinth: "!FILE!" name=!DISPLAY_NAME! version=!VERSION_NUMBER! mc=!MC_VERSION! loader=!LOADER!
    if defined KEEP_JSON (
        echo [info] Kept request JSON: !MR_DATA!
    ) else (
        del "!MR_DATA!" >nul 2>&1
    )
    exit /b 0
)
set "MR_RESP=%TEMP%\mr_resp_%RANDOM%.json"
for /f %%H in ('curl -s -o "!MR_RESP!" -w "%%{http_code}" -X POST "https://api.modrinth.com/v2/version" -H "Authorization: %MODRINTH_TOKEN%" -F "data=@\"!MR_DATA!\";type=application/json" -F "file=@\"!FILE!\""') do set "HTTP=%%H"
if "!HTTP!"=="200" goto :mr_ok
if "!HTTP!"=="201" goto :mr_ok
echo [error] Modrinth upload failed for "!FILE!" (HTTP !HTTP!):
type "!MR_RESP!"
echo [info] Kept response: !MR_RESP!
echo [info] Kept request JSON: !MR_DATA!
exit /b 1
:mr_ok
echo [ok] Modrinth upload succeeded (HTTP !HTTP!).
if defined KEEP_JSON (
    echo [info] Kept response: !MR_RESP!
    echo [info] Kept request JSON: !MR_DATA!
) else (
    del "!MR_RESP!" >nul 2>&1
    del "!MR_DATA!" >nul 2>&1
)
exit /b 0

:readChangelog
set "CHANGELOG_JSON="
for /f "usebackq delims=" %%L in ("%~1") do (
    set "LINE=%%L"
    set "LINE=!LINE:\=\\!"
    set "LINE=!LINE:\"=\\\"!"
    set "LINE=!LINE:%%=%%%%!"
    if defined CHANGELOG_JSON (
        set "CHANGELOG_JSON=!CHANGELOG_JSON!\n!LINE!"
    ) else (
        set "CHANGELOG_JSON=!LINE!"
    )
)
if not defined CHANGELOG_JSON exit /b 1
exit /b 0

:requireEnv
set "VAR_NAME=%~1"
if not defined %VAR_NAME% (
    echo [error] Environment variable "%VAR_NAME%" is required.
    set "MISSING_ENV=1"
)
exit /b 0

:checkLimit
if defined UPLOAD_LIMIT (
    if "!UPLOAD_LIMIT!"=="" exit /b 0
    if !UPLOADED! GEQ !UPLOAD_LIMIT! (
        set "STOP_UPLOADS=1"
        echo [info] Upload limit reached ^(!UPLOAD_LIMIT!^); stopping.
    )
)
exit /b 0

:finish
set "EXIT_CODE=%ERRORLEVEL%"
if defined MISSING_ENV set "EXIT_CODE=1"
if "%EXIT_CODE%"=="0" (
    echo.
    echo Uploads complete.
) else (
    echo.
    echo Upload script ended with errors.
)
if not defined VEINMINER_NO_PAUSE pause
exit /b %EXIT_CODE%
