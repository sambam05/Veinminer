@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM === Configuration ===
REM Required values (set in environment before calling):
REM   CURSEFORGE_TOKEN
REM   CURSEFORGE_PROJECT_ID
REM Optional environment variables:
REM   CHANGELOG_FILE=
REM   CURSEFORGE_RELEASE_TYPE=release
REM   CURSEFORGE_CHANGELOG_TYPE=markdown
REM   CURSEFORGE_RELATIONS=              (raw JSON; omitted when blank)
REM   CURSEFORGE_EXTRA_TAGS=             (space separated extra gameVersion tags)
REM   CURSEFORGE_VERSION_FILE=           (reuse a pre-fetched /api/game/versions payload)
REM   CURSEFORGE_CURL_TRACE=1            (optional: writes curl trace; may include auth headers)
REM   CURSEFORGE_CONNECT_TIMEOUT=20      (seconds to wait for TCP/TLS connect)
REM   CURSEFORGE_DOWNLOAD_TIMEOUT=60     (seconds for version-list request)
REM   CURSEFORGE_UPLOAD_TIMEOUT=300      (seconds for upload request)
REM   CURSEFORGE_STALL_TIMEOUT=90        (seconds of near-zero transfer before abort)
REM   DRY_RUN                            - if set, skip HTTP calls and only log what would upload
REM   UPLOAD_LIMIT                       - stop after N uploads (blank for no limit)
REM   TARGET_UPLOADS                     - fabric | neoforge | both (prompted when missing)
REM   RELEASE_SECRETS_FILE               - env file path (default: .\secrets\release-secrets.env)
REM
REM The script scans artifacts\dist-neoforge and artifacts\dist-fabric for *.jar, parses the name
REM Veinminer-<loader>-<modVersion>+mc<mcVersion>.jar, then posts each file to
REM CurseForge using the provided token and changelog.

REM Pre-set release defaults (non-secret)
if not defined CURSEFORGE_PROJECT_ID set "CURSEFORGE_PROJECT_ID=1296186"
if not defined CURSEFORGE_RELEASE_TYPE set "CURSEFORGE_RELEASE_TYPE=beta"
if "%CURSEFORGE_RELEASE_TYPE%"=="" set "CURSEFORGE_RELEASE_TYPE=beta"
if not defined CURSEFORGE_CHANGELOG_TYPE set "CURSEFORGE_CHANGELOG_TYPE=markdown"
if "%CURSEFORGE_CHANGELOG_TYPE%"=="" set "CURSEFORGE_CHANGELOG_TYPE=markdown"
if not defined CURSEFORGE_RELATIONS set "CURSEFORGE_RELATIONS="
if not defined CURSEFORGE_EXTRA_TAGS set "CURSEFORGE_EXTRA_TAGS="
if not defined CURSEFORGE_CURL_TRACE set "CURSEFORGE_CURL_TRACE="
if not defined CURSEFORGE_CONNECT_TIMEOUT set "CURSEFORGE_CONNECT_TIMEOUT=20"
if not defined CURSEFORGE_DOWNLOAD_TIMEOUT set "CURSEFORGE_DOWNLOAD_TIMEOUT=60"
if not defined CURSEFORGE_UPLOAD_TIMEOUT set "CURSEFORGE_UPLOAD_TIMEOUT=300"
if not defined CURSEFORGE_STALL_TIMEOUT set "CURSEFORGE_STALL_TIMEOUT=90"
if not defined DRY_RUN set "DRY_RUN="
if not defined UPLOAD_LIMIT set "UPLOAD_LIMIT="
if not defined TARGET_UPLOADS set "TARGET_UPLOADS=both"

set "CF_VERSION_FILE="
if defined CURSEFORGE_VERSION_FILE set "CF_VERSION_FILE=%CURSEFORGE_VERSION_FILE%"

for %%I in ("%~dp0..") do set "ROOT=%%~fI\"
if not defined CHANGELOG_FILE set "CHANGELOG_FILE=%ROOT%docs\CHANGELOG.md"
if "%CHANGELOG_FILE%"=="" set "CHANGELOG_FILE=%ROOT%docs\CHANGELOG.md"
if not defined RELEASE_SECRETS_FILE set "RELEASE_SECRETS_FILE=%ROOT%secrets\release-secrets.env"

call :loadSecretsFile "%RELEASE_SECRETS_FILE%"

REM Load supported upload versions from shared metadata.
call "%ROOT%config\version-metadata.bat" upload
if errorlevel 1 goto :finish
if not defined NEO_VERSIONS (
    echo [error] NEO_VERSIONS was not provided by version-metadata.bat
    goto :finish
)
if not defined FAB_VERSIONS (
    echo [error] FAB_VERSIONS was not provided by version-metadata.bat
    goto :finish
)

REM Known CurseForge gameVersionTypeIDs for Minecraft mainline releases (newest-first)
set "MC_VERSION_TYPES=77784 75125 73407 73250 73242 70886 68722 64806 55023 628 572 599 552 17 16 15 14 13 12 11 6 5 4"

where curl >nul 2>nul
if errorlevel 1 (
    echo [error] curl is not available in PATH.
    goto :finish
)

call :requireEnv CURSEFORGE_TOKEN
call :requireEnv CURSEFORGE_PROJECT_ID
if defined MISSING_ENV goto :finish

call :validatePositiveInt CURSEFORGE_CONNECT_TIMEOUT
if errorlevel 1 goto :finish
call :validatePositiveInt CURSEFORGE_DOWNLOAD_TIMEOUT
if errorlevel 1 goto :finish
call :validatePositiveInt CURSEFORGE_UPLOAD_TIMEOUT
if errorlevel 1 goto :finish
call :validatePositiveInt CURSEFORGE_STALL_TIMEOUT
if errorlevel 1 goto :finish

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

call :loadCfVersionList
if errorlevel 1 goto :finish

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

if defined DRY_RUN (
    echo [info] DRY_RUN is set; uploads will not be sent.
)

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
    call :processList artifacts\dist-neoforge "!NEO_VERSIONS!"
    if errorlevel 1 goto :finish
    if defined STOP_UPLOADS goto :finish
)
if defined PROCESS_FABRIC (
    call :processList artifacts\dist-fabric "!FAB_VERSIONS!"
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
call :uploadCurseforge
if errorlevel 1 exit /b 1
exit /b 0

:uploadCurseforge
set "LOADER_TAG=!LOADER!"
if /I "!LOADER!"=="fabric" set "LOADER_TAG=Fabric"
if /I "!LOADER!"=="neoforge" set "LOADER_TAG=NeoForge"

call :resolveGameVersions
if errorlevel 1 exit /b 1

set "CF_META=%TEMP%\cf_meta_%RANDOM%.json"
set "CHANGELOG_ESC=!CHANGELOG_JSON!"
set "CHANGELOG_ESC=!CHANGELOG_ESC:\=\\!"
set "CHANGELOG_ESC=!CHANGELOG_ESC:"=\\\"!"
set "CHANGELOG_ESC=!CHANGELOG_ESC:\\n=\n!"
set "DISPLAY_ESC=!DISPLAY_NAME!"
set "DISPLAY_ESC=!DISPLAY_ESC:\=\\!"
set "DISPLAY_ESC=!DISPLAY_ESC:"=\\\"!"

if defined CURSEFORGE_RELATIONS (
    > "!CF_META!" (
        echo {
        echo   "displayName":"!DISPLAY_ESC!",
        echo   "gameVersions":!GAME_VERSIONS_JSON!,
        echo   "releaseType":"%CURSEFORGE_RELEASE_TYPE%",
        echo   "changelog":"!CHANGELOG_ESC!",
        echo   "changelogType":"%CURSEFORGE_CHANGELOG_TYPE%",
        echo   "relations":%CURSEFORGE_RELATIONS%
        echo }
    )
) else (
    > "!CF_META!" (
        echo {
        echo   "displayName":"!DISPLAY_ESC!",
        echo   "gameVersions":!GAME_VERSIONS_JSON!,
        echo   "releaseType":"%CURSEFORGE_RELEASE_TYPE%",
        echo   "changelog":"!CHANGELOG_ESC!",
        echo   "changelogType":"%CURSEFORGE_CHANGELOG_TYPE%"
        echo }
    )
)

if defined DRY_RUN (
    echo [dry-run] CurseForge: "!FILE!" name=!DISPLAY_NAME! version=!VERSION_NUMBER! mc=!MC_VERSION! loader=!LOADER_TAG! releaseType=%CURSEFORGE_RELEASE_TYPE%
    del "!CF_META!" >nul 2>&1
    exit /b 0
)

set "CF_RESP=%TEMP%\cf_resp_%RANDOM%.json"
for %%S in ("!CF_META!") do (
    set "CF_META_SHORT=%%~sS"
    if "!CF_META_SHORT!"=="" set "CF_META_SHORT=%%~fS"
)

set "CF_TRACE="
set "HTTP="
echo [info] Upload timeouts: connect=!CURSEFORGE_CONNECT_TIMEOUT!s total=!CURSEFORGE_UPLOAD_TIMEOUT!s stall=!CURSEFORGE_STALL_TIMEOUT!s
if defined CURSEFORGE_CURL_TRACE (
    set "CF_TRACE=%TEMP%\cf_trace_%RANDOM%.log"
    for /f %%H in ('curl.exe -sS --connect-timeout !CURSEFORGE_CONNECT_TIMEOUT! --max-time !CURSEFORGE_UPLOAD_TIMEOUT! --speed-time !CURSEFORGE_STALL_TIMEOUT! --speed-limit 1 --trace-ascii "!CF_TRACE!" -o "!CF_RESP!" -w "%%{http_code}" "https://minecraft.curseforge.com/api/projects/%CURSEFORGE_PROJECT_ID%/upload-file" -H "X-Api-Token: %CURSEFORGE_TOKEN%" -F "metadata=<!CF_META_SHORT!;type=application/json" -F "file=@!FILE!;type=application/java-archive"') do set "HTTP=%%H"
) else (
    for /f %%H in ('curl.exe -sS --connect-timeout !CURSEFORGE_CONNECT_TIMEOUT! --max-time !CURSEFORGE_UPLOAD_TIMEOUT! --speed-time !CURSEFORGE_STALL_TIMEOUT! --speed-limit 1 -o "!CF_RESP!" -w "%%{http_code}" "https://minecraft.curseforge.com/api/projects/%CURSEFORGE_PROJECT_ID%/upload-file" -H "X-Api-Token: %CURSEFORGE_TOKEN%" -F "metadata=<!CF_META_SHORT!;type=application/json" -F "file=@!FILE!;type=application/java-archive"') do set "HTTP=%%H"
)
if "!HTTP!"=="200" goto :cf_ok
if "!HTTP!"=="201" goto :cf_ok
if "!HTTP!"=="" set "HTTP=000"
echo [error] CurseForge upload failed for "!FILE!" (HTTP !HTTP!):
if exist "!CF_RESP!" type "!CF_RESP!"
echo [info] Kept metadata: !CF_META!
echo [info] Kept response: !CF_RESP!
if defined CF_TRACE if exist "!CF_TRACE!" echo [info] Curl trace: !CF_TRACE!
exit /b 1

:cf_ok
echo [ok] CurseForge upload succeeded (HTTP !HTTP!).
del "!CF_RESP!" >nul 2>&1
del "!CF_META!" >nul 2>&1
if defined CF_TRACE del "!CF_TRACE!" >nul 2>&1
exit /b 0

:loadCfVersionList
if defined CF_VERSION_FILE (
    if exist "!CF_VERSION_FILE!" (
        echo [info] Using CurseForge version list from "!CF_VERSION_FILE!"
        exit /b 0
    ) else (
        echo [warn] CURSEFORGE_VERSION_FILE "!CF_VERSION_FILE!" not found; downloading instead.
        set "CF_VERSION_FILE="
    )
)
set "CF_VERSION_FILE_CLEANUP="
if not defined CF_VERSION_FILE (
    set "CF_VERSION_FILE=%TEMP%\cf_versions_%RANDOM%.json"
    set "CF_VERSION_FILE_CLEANUP=1"
    echo [info] Downloading CurseForge game version list...
    call :downloadCfVersions "!CF_VERSION_FILE!"
    if errorlevel 1 exit /b 1
)
for %%S in ("!CF_VERSION_FILE!") do (
    if not exist "%%~fS" (
        echo [error] CurseForge game version list was not created: "%%~fS"
        exit /b 1
    )
    if %%~zS LEQ 0 (
        echo [error] CurseForge game version list is empty: "%%~fS"
        exit /b 1
    )
)
exit /b 0

:downloadCfVersions
set "CF_HTTP_STATUS="
echo [info] Version-list timeout: connect=!CURSEFORGE_CONNECT_TIMEOUT!s total=!CURSEFORGE_DOWNLOAD_TIMEOUT!s
for /f %%H in ('curl.exe -sS --connect-timeout !CURSEFORGE_CONNECT_TIMEOUT! --max-time !CURSEFORGE_DOWNLOAD_TIMEOUT! -o "%~1" -w "%%{http_code}" -H "X-Api-Token: %CURSEFORGE_TOKEN%" "https://minecraft.curseforge.com/api/game/versions"') do set "CF_HTTP_STATUS=%%H"
if "!CF_HTTP_STATUS!"=="200" exit /b 0
if "!CF_HTTP_STATUS!"=="" set "CF_HTTP_STATUS=000"
echo [error] Failed to download CurseForge game version list (HTTP !CF_HTTP_STATUS!).
exit /b 1

:resolveGameVersions
call :getVersionId "!MC_VERSION!" CF_MC_ID "!MC_VERSION_TYPES!" 0
if errorlevel 1 exit /b 1
call :getVersionId "!LOADER_TAG!" CF_LOADER_ID "68441"
if errorlevel 1 exit /b 1
set "GAME_VERSIONS_JSON=[!CF_MC_ID!,!CF_LOADER_ID!]"
if defined CURSEFORGE_EXTRA_TAGS (
    for %%T in (!CURSEFORGE_EXTRA_TAGS!) do (
        call :getVersionId "%%~T" CF_EXTRA_ID ""
        if errorlevel 1 exit /b 1
        set "GAME_VERSIONS_JSON=!GAME_VERSIONS_JSON:]=,!CF_EXTRA_ID!]!"
    )
)
exit /b 0

:getVersionId
set "CF_TMP_ID="
set "CF_LOOKUP=%~1"
set "CF_PREF_TYPES=%~3"
set "CF_ALLOW_ANY=%~4"
if not defined CF_ALLOW_ANY set "CF_ALLOW_ANY=1"
for /f "usebackq delims=" %%I in (`powershell -NoLogo -NoProfile -Command "$ErrorActionPreference='Stop';$name=$env:CF_LOOKUP;$file=$env:CF_VERSION_FILE;$pref=$env:CF_PREF_TYPES;$allowAny=[int]$env:CF_ALLOW_ANY; $data=Get-Content -Raw $file | ConvertFrom-Json; $entry=$null; if($pref){ $prefTypes=$pref -split ' '; foreach($t in $prefTypes){ if([string]::IsNullOrWhiteSpace($t)){ continue }; $hit=$data | Where-Object { $_.name -eq $name -and $_.gameVersionTypeID -eq [int]$t } | Sort-Object id -Descending | Select-Object -First 1; if($hit){ $entry=$hit; break } } }; if(-not $entry -and $allowAny){ $entry=$data | Where-Object { $_.name -eq $name } | Sort-Object id -Descending | Select-Object -First 1 }; if($entry){ $entry.id } else { exit 1 }"`) do set "CF_TMP_ID=%%I"
if not defined CF_TMP_ID (
    echo [error] Could not resolve CurseForge version id for "%~1".
    exit /b 1
)
set "%~2=!CF_TMP_ID!"
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

:loadSecretsFile
set "SECRETS_FILE=%~1"
if "%SECRETS_FILE%"=="" exit /b 0
if not exist "%SECRETS_FILE%" exit /b 0
for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%SECRETS_FILE%") do (
    if not "%%~A"=="" (
        if not defined %%~A set "%%~A=%%~B"
    )
)
exit /b 0

:requireEnv
set "VAR_NAME=%~1"
if not defined %VAR_NAME% (
    echo [error] Environment variable "%VAR_NAME%" is required.
    set "MISSING_ENV=1"
)
exit /b 0

:validatePositiveInt
set "INT_VAR=%~1"
set "INT_VAL=!%INT_VAR%!"
if "!INT_VAL!"=="" (
    echo [error] !INT_VAR! must not be empty.
    exit /b 1
)
echo !INT_VAL!| findstr /R "^[0-9][0-9]*$" >nul
if errorlevel 1 (
    echo [error] !INT_VAR! must be a positive integer.
    exit /b 1
)
if !INT_VAL! LEQ 0 (
    echo [error] !INT_VAR! must be greater than zero.
    exit /b 1
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
if defined CF_VERSION_FILE_CLEANUP if exist "!CF_VERSION_FILE!" del "!CF_VERSION_FILE!" >nul 2>&1
if not defined VEINMINER_NO_PAUSE pause
exit /b %EXIT_CODE%
