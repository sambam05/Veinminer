@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM === Configuration ===
REM Required values (override by exporting before calling):
REM   CURSEFORGE_TOKEN
REM   CURSEFORGE_PROJECT_ID
REM Optional environment variables:
REM   CHANGELOG_FILE=
REM   CURSEFORGE_RELEASE_TYPE=release
REM   CURSEFORGE_CHANGELOG_TYPE=markdown
REM   CURSEFORGE_RELATIONS=              (raw JSON; omitted when blank)
REM   CURSEFORGE_EXTRA_TAGS=                  (space separated extra gameVersions tags)
REM   CURSEFORGE_VERSION_FILE=                (reuse a pre-fetched https://minecraft.curseforge.com/api/game/versions payload)
REM   DRY_RUN                                 - if set, skip HTTP calls and only log what would upload
REM   UPLOAD_LIMIT                            - stop after N uploads (blank for no limit)
REM   TARGET_UPLOADS                          - fabric | neoforge | both (prompted when missing)
REM
REM The script scans dist-neoforge and dist-fabric for *.jar, parses the name
REM Veinminer-<loader>-<modVersion>+mc<mcVersion>.jar, then posts each file to
REM CurseForge using the provided token and changelog.

REM Pre-set release defaults

set "TARGET_UPLOADS=both"
if not defined CURSEFORGE_RELEASE_TYPE set "CURSEFORGE_RELEASE_TYPE=release"
if "%CURSEFORGE_RELEASE_TYPE%"=="" set "CURSEFORGE_RELEASE_TYPE=release"
if not defined CURSEFORGE_CHANGELOG_TYPE set "CURSEFORGE_CHANGELOG_TYPE=markdown"
if "%CURSEFORGE_CHANGELOG_TYPE%"=="" set "CURSEFORGE_CHANGELOG_TYPE=markdown"
if not defined CURSEFORGE_RELATIONS set "CURSEFORGE_RELATIONS="
if not defined CURSEFORGE_EXTRA_TAGS set "CURSEFORGE_EXTRA_TAGS="
if not defined DRY_RUN set "DRY_RUN="
if not defined CHANGELOG_FILE set "CHANGELOG_FILE=C:\Programming\VMM Rebuild\changelog.md"
if not defined UPLOAD_LIMIT set "UPLOAD_LIMIT="
set "CF_VERSION_FILE="
if defined CURSEFORGE_VERSION_FILE set "CF_VERSION_FILE=%CURSEFORGE_VERSION_FILE%"

set "ROOT=%~dp0"
if "%CHANGELOG_FILE%"=="" set "CHANGELOG_FILE=%ROOT%changelog.md"

REM Known CurseForge gameVersionTypeIDs for Minecraft mainline releases (newest-first)
set "MC_VERSION_TYPES=77784 75125 73407 73250 73242 70886 68722 64806 55023 628 572 599 552 17 16 15 14 13 12 11 6 5 4"
set "NEO_VERSIONS=1.21 1.21.1 1.21.2 1.21.3 1.21.4 1.21.5 1.21.6 1.21.7 1.21.8 1.21.9 1.21.10"
set "FAB_VERSIONS=1.20 1.20.1 1.20.2 1.20.3 1.20.4 1.20.5 1.20.6 1.21 1.21.1 1.21.2 1.21.3 1.21.4 1.21.5 1.21.6 1.21.7 1.21.8 1.21.9 1.21.10"

where curl >nul 2>nul
if errorlevel 1 (
    echo [error] curl is not available in PATH.
    goto :finish
)

call :requireEnv CURSEFORGE_TOKEN
call :requireEnv CURSEFORGE_PROJECT_ID
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

call :loadVersionList
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

echo Using changelog from "%CHANGELOG_FILE%"
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
call :uploadCurseforge
if errorlevel 1 exit /b 1
exit /b 0

:uploadCurseforge
set "LOADER_TAG=!LOADER!"
if /I "!LOADER!"=="fabric" set "LOADER_TAG=Fabric"
if /I "!LOADER!"=="neoforge" set "LOADER_TAG=NeoForge"

call :resolveGameVersions
if errorlevel 1 exit /b 1

set "CF_META=%TEMP%\\cf_meta_%RANDOM%.json"
> "!CF_META!" (
    echo {
    echo   "displayName":"!DISPLAY_NAME!",
    echo   "gameVersions":!GAME_VERSIONS_JSON!,
    echo   "releaseType":"%CURSEFORGE_RELEASE_TYPE%",
    echo   "changelog":"!CHANGELOG_JSON!",
    echo   "changelogType":"%CURSEFORGE_CHANGELOG_TYPE%"
    if defined CURSEFORGE_RELATIONS if not "%CURSEFORGE_RELATIONS%"=="" echo   ,"relations":%CURSEFORGE_RELATIONS%
    echo }
)
if defined DRY_RUN (
    echo [dry-run] CurseForge: "!FILE!" name=!DISPLAY_NAME! version=!VERSION_NUMBER! mc=!MC_VERSION! loader=!LOADER_TAG! releaseType=%CURSEFORGE_RELEASE_TYPE%
    del "!CF_META!" >nul 2>&1
    exit /b 0
)
set "CF_RESP=%TEMP%\\cf_resp_%RANDOM%.json"
for /f %%H in ('curl.exe -s -o "!CF_RESP!" -w "%%{http_code}" -X POST "https://minecraft.curseforge.com/api/projects/%CURSEFORGE_PROJECT_ID%/upload-file" -H "X-Api-Token: %CURSEFORGE_TOKEN%" -F "metadata=<\"!CF_META!\"" -F "file=@\"!FILE!\""') do set "HTTP=%%H"
if "!HTTP!"=="200" goto :cf_ok
if "!HTTP!"=="201" goto :cf_ok
echo [error] CurseForge upload failed for "!FILE!" (HTTP !HTTP!):
type "!CF_RESP!"
del "!CF_RESP!" >nul 2>&1
del "!CF_META!" >nul 2>&1
exit /b 1
:cf_ok
echo [ok] CurseForge upload succeeded (HTTP !HTTP!).
del "!CF_RESP!" >nul 2>&1
del "!CF_META!" >nul 2>&1
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

:loadVersionList
set "CF_VER_HTTP="
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
    set "CF_VERSION_FILE=%TEMP%\\cf_versions_%RANDOM%.json"
    set "CF_VERSION_FILE_CLEANUP=1"
    echo [info] Downloading CurseForge game version list...
    for /f %%H in ('curl.exe -s -o "!CF_VERSION_FILE!" -w "%%{http_code}" -H "X-Api-Token: %CURSEFORGE_TOKEN%" "https://minecraft.curseforge.com/api/game/versions"') do set "CF_VER_HTTP=%%H"
    if not "!CF_VER_HTTP!"=="200" (
        echo [error] Failed to download CurseForge game version list ^(HTTP !CF_VER_HTTP!^).
        exit /b 1
    )
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
if defined CF_VERSION_FILE_CLEANUP if exist "!CF_VERSION_FILE!" del "!CF_VERSION_FILE!" >nul 2>&1
if not defined VEINMINER_NO_PAUSE pause
exit /b %EXIT_CODE%
