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
REM   CURSEFORGE_CURL_TRACE=1                 (optional: writes curl trace; may include auth headers)
REM   DRY_RUN                                 - if set, skip HTTP calls and only log what would upload
REM   UPLOAD_LIMIT                            - stop after N uploads (blank for no limit)
REM   TARGET_UPLOADS                          - fabric | neoforge | both (prompted when missing)
REM
REM The script scans dist-neoforge and dist-fabric for *.jar, parses the name
REM Veinminer-<loader>-<modVersion>+mc<mcVersion>.jar, then posts each file to
REM CurseForge using the provided token and changelog.

REM Pre-set release defaults (non-secret)
set "CURSEFORGE_PROJECT_ID=1296186"
set "TARGET_UPLOADS=both"
if not defined CURSEFORGE_RELEASE_TYPE set "CURSEFORGE_RELEASE_TYPE=beta"
if "%CURSEFORGE_RELEASE_TYPE%"=="" set "CURSEFORGE_RELEASE_TYPE=beta"
if not defined CURSEFORGE_CHANGELOG_TYPE set "CURSEFORGE_CHANGELOG_TYPE=markdown"
if "%CURSEFORGE_CHANGELOG_TYPE%"=="" set "CURSEFORGE_CHANGELOG_TYPE=markdown"
if not defined CURSEFORGE_RELATIONS set "CURSEFORGE_RELATIONS="
if not defined CURSEFORGE_EXTRA_TAGS set "CURSEFORGE_EXTRA_TAGS="
if not defined CURSEFORGE_CURL_TRACE set "CURSEFORGE_CURL_TRACE="
if not defined DRY_RUN set "DRY_RUN="
if not defined CHANGELOG_FILE set "CHANGELOG_FILE=C:\Programming\VMM Rebuild\changelog.md"
if not defined UPLOAD_LIMIT set "UPLOAD_LIMIT="
if not defined USE_PYTHON_HTTP set "USE_PYTHON_HTTP="
set "CF_VERSION_FILE="
if defined CURSEFORGE_VERSION_FILE set "CF_VERSION_FILE=%CURSEFORGE_VERSION_FILE%"

set "ROOT=%~dp0"
if "%CHANGELOG_FILE%"=="" set "CHANGELOG_FILE=%ROOT%changelog.md"

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

REM Known CurseForge gameVersionTypeIDs for Minecraft mainline releases (newest-first)
set "MC_VERSION_TYPES=77784 75125 73407 73250 73242 70886 68722 64806 55023 628 572 599 552 17 16 15 14 13 12 11 6 5 4"

set "HTTP_IMPL=curl"
if defined USE_PYTHON_HTTP set "HTTP_IMPL=python"

call :requireEnv CURSEFORGE_TOKEN
call :requireEnv CURSEFORGE_PROJECT_ID
if defined MISSING_ENV goto :finish

call :chooseHttpClient
if defined DEBUG_LOG echo [debug] chose HTTP client !HTTP_IMPL!, errorlevel=!errorlevel!
if errorlevel 1 goto :finish

if not exist "%CHANGELOG_FILE%" (
    echo [error] Changelog file not found: "%CHANGELOG_FILE%"
    goto :finish
)

call :readChangelog "%CHANGELOG_FILE%"
if defined DEBUG_LOG echo [debug] read changelog, errorlevel=!errorlevel!
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
if defined DEBUG_LOG echo [debug] loaded version list, errorlevel=!errorlevel!
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
if defined DEBUG_LOG echo [debug] targets: !TARGET_CHOICE!, process fabric=!PROCESS_FABRIC! neoforge=!PROCESS_NEOFORGE!

if defined DRY_RUN (
    echo [info] DRY_RUN is set; uploads will not be sent.
)

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

:chooseHttpClient
if /I "!HTTP_IMPL!"=="curl" (
    where curl >nul 2>nul
    if errorlevel 1 (
        echo [warn] curl not found in PATH; switching to Python HTTP client.
        set "HTTP_IMPL=python"
    )
)
if /I "!HTTP_IMPL!"=="curl" (
    curl.exe -s https://example.com >nul 2>nul
    if errorlevel 1 (
        echo [warn] curl HTTPS probe failed; switching to Python HTTP client.
        set "HTTP_IMPL=python"
    )
)
if /I "!HTTP_IMPL!"=="python" (
    python -c "import urllib.request; urllib.request.urlopen('https://example.com')" >nul 2>nul
    if errorlevel 1 (
        echo [error] Python HTTPS probe failed. Fix Python SSL or set USE_PYTHON_HTTP=curl.
        exit /b 1
    )
)
echo [info] HTTP client: !HTTP_IMPL!
exit /b 0

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
if defined DEBUG_LOG echo [debug] uploadCurseforge start loader=!LOADER! mc=!MC_VERSION! version=!VERSION_NUMBER!
call :uploadCurseforge
if errorlevel 1 exit /b 1
exit /b 0

:uploadCurseforge
set "LOADER_TAG=!LOADER!"
if /I "!LOADER!"=="fabric" set "LOADER_TAG=Fabric"
if /I "!LOADER!"=="neoforge" set "LOADER_TAG=NeoForge"

call :resolveGameVersions
if defined DEBUG_LOG echo [debug] resolved versions mcId=!CF_MC_ID! loaderId=!CF_LOADER_ID! extras=!CURSEFORGE_EXTRA_TAGS!
if errorlevel 1 exit /b 1

set "CF_META=%TEMP%\cf_meta_%RANDOM%.json"
if defined DEBUG_LOG echo [debug] metadata path: !CF_META!
powershell -NoLogo -NoProfile -Command ^
  "$ErrorActionPreference='Stop';" ^
  "$changelog = '!CHANGELOG_JSON!';" ^
  "$relations = $null; if('!CURSEFORGE_RELATIONS!' -ne ''){ $relations = ConvertFrom-Json '!CURSEFORGE_RELATIONS!'; }" ^
  "$gameVersions = @(); $rawIds = '!GAME_VERSIONS_JSON!'.Trim('[]'); if($rawIds -ne ''){ $gameVersions = $rawIds.Split(',') | ForEach-Object { [int]($_.Trim()) } };" ^
  "$data = [ordered]@{displayName='!DISPLAY_NAME!';gameVersions=$gameVersions;releaseType='!CURSEFORGE_RELEASE_TYPE!';changelog=$changelog;changelogType='!CURSEFORGE_CHANGELOG_TYPE!'};" ^
  "if($relations){ $data['relations']=$relations }" ^
  "$json = $data | ConvertTo-Json -Depth 8 -Compress;" ^
  "$utf8NoBom = New-Object System.Text.UTF8Encoding($false);" ^
  "[System.IO.File]::WriteAllText('!CF_META!', $json, $utf8NoBom)"
if defined DEBUG_LOG echo [debug] after metadata write, errorlevel=!ERRORLEVEL!, DRY_RUN=!DRY_RUN!
if errorlevel 1 (
    echo [error] Failed to create metadata file: !CF_META!
    exit /b 1
)
if defined DRY_RUN (
    echo [dry-run] CurseForge: "!FILE!" name=!DISPLAY_NAME! version=!VERSION_NUMBER! mc=!MC_VERSION! loader=!LOADER_TAG! releaseType=%CURSEFORGE_RELEASE_TYPE%
    del "!CF_META!" >nul 2>&1
    exit /b 0
)
set "CF_RESP=%TEMP%\cf_resp_%RANDOM%.json"
if not exist "!CF_META!" (
    echo [error] Metadata file missing: !CF_META!
    exit /b 1
)
for %%S in ("!CF_META!") do (
    set "CF_META_SHORT=%%~sS"
    if "!CF_META_SHORT!"=="" set "CF_META_SHORT=%%~fS"
    echo [info] Metadata file: %%~fS ^(%%~zS bytes^) short=!CF_META_SHORT!
)
set "CF_TRACE="
set "HTTP="
if /I "!HTTP_IMPL!"=="curl" (
    if defined CURSEFORGE_CURL_TRACE (
        set "CF_TRACE=%TEMP%\cf_trace_%RANDOM%.log"
        for /f %%H in ('curl.exe %CURL_OPTS% --trace-ascii "!CF_TRACE!" -o "!CF_RESP!" -w "%%{http_code}" "https://minecraft.curseforge.com/api/projects/%CURSEFORGE_PROJECT_ID%/upload-file" -H "X-Api-Token: %CURSEFORGE_TOKEN%" -F "metadata=<!CF_META_SHORT!;type=application/json" -F "file=@!FILE!;type=application/java-archive"') do set "HTTP=%%H"
    ) else (
        for /f %%H in ('curl.exe %CURL_OPTS% -o "!CF_RESP!" -w "%%{http_code}" "https://minecraft.curseforge.com/api/projects/%CURSEFORGE_PROJECT_ID%/upload-file" -H "X-Api-Token: %CURSEFORGE_TOKEN%" -F "metadata=<!CF_META_SHORT!;type=application/json" -F "file=@!FILE!;type=application/java-archive"') do set "HTTP=%%H"
    )
    if "!HTTP!"=="200" goto :cf_ok
    if "!HTTP!"=="201" goto :cf_ok
    echo [warn] CurseForge upload via curl failed for "!FILE!" (HTTP !HTTP!^); retrying with Python client...
)
if /I "!HTTP_IMPL!"=="python" (
    call :uploadWithPython "!CF_META_SHORT!" "!FILE!" "!CF_RESP!"
    if not "!CF_HTTP_STATUS!"=="" set "HTTP=!CF_HTTP_STATUS!"
    if "!HTTP!"=="200" goto :cf_ok
    if "!HTTP!"=="201" goto :cf_ok
)
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

:loadCfVersionList
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
if /I "!HTTP_IMPL!"=="curl" (
    for /f %%H in ('curl.exe -s -o "%~1" -w "%%{http_code}" -H "X-Api-Token: %CURSEFORGE_TOKEN%" "https://minecraft.curseforge.com/api/game/versions"') do set "CF_HTTP_STATUS=%%H"
    if "!CF_HTTP_STATUS!"=="200" exit /b 0
    echo [warn] curl download failed (HTTP !CF_HTTP_STATUS!^); retrying with Python client...
)
call :downloadWithPython "%~1" "https://minecraft.curseforge.com/api/game/versions"
if errorlevel 1 exit /b 1
if not "!CF_HTTP_STATUS!"=="200" (
    echo [error] Failed to download CurseForge game version list ^(HTTP !CF_HTTP_STATUS!^).
    exit /b 1
)
exit /b 0

:downloadWithPython
set "HTTP_OUT=%~1"
set "HTTP_URL=%~2"
set "CF_HTTP_STATUS="
set "PY_HTTP=%TEMP%\cf_dl_%RANDOM%.py"
> "!PY_HTTP!" echo import os, sys, urllib.request, urllib.error
>> "!PY_HTTP!" echo out_path = os.environ["HTTP_OUT"]
>> "!PY_HTTP!" echo url = os.environ["HTTP_URL"]
>> "!PY_HTTP!" echo token = os.environ.get("CURSEFORGE_TOKEN","")
>> "!PY_HTTP!" echo headers = {"X-Api-Token": token} if token else {}
>> "!PY_HTTP!" echo req = urllib.request.Request(url, headers=headers)
>> "!PY_HTTP!" echo try:
>> "!PY_HTTP!" echo ^    with urllib.request.urlopen(req) as resp:
>> "!PY_HTTP!" echo ^        data = resp.read()
>> "!PY_HTTP!" echo ^        open(out_path, "wb").write(data)
>> "!PY_HTTP!" echo ^        print(resp.getcode())
>> "!PY_HTTP!" echo ^        sys.exit(0)
>> "!PY_HTTP!" echo except urllib.error.HTTPError as exc:
>> "!PY_HTTP!" echo ^    print(getattr(exc, "code", 0))
>> "!PY_HTTP!" echo ^    try:
>> "!PY_HTTP!" echo ^        open(out_path, "wb").write(exc.read())
>> "!PY_HTTP!" echo ^    except Exception:
>> "!PY_HTTP!" echo ^        pass
>> "!PY_HTTP!" echo ^    sys.exit(1)
>> "!PY_HTTP!" echo except Exception as exc:
>> "!PY_HTTP!" echo ^    sys.stderr.write(str(exc))
>> "!PY_HTTP!" echo ^    sys.exit(1)
for /f %%H in ('python "!PY_HTTP!"') do set "CF_HTTP_STATUS=%%H"
set "PY_RC=%ERRORLEVEL%"
del "!PY_HTTP!" >nul 2>&1
if not defined CF_HTTP_STATUS set "CF_HTTP_STATUS="
if not "%PY_RC%"=="0" exit /b 1
exit /b 0

:uploadWithPython
set "CF_META_FILE=%~1"
set "CF_UPLOAD_FILE=%~2"
set "CF_RESP_FILE=%~3"
set "CF_HTTP_STATUS="
set "PY_HTTP=%TEMP%\cf_up_%RANDOM%.py"
> "!PY_HTTP!" echo import os, sys, uuid, urllib.request, urllib.error
>> "!PY_HTTP!" echo meta_path = os.environ["CF_META_FILE"]
>> "!PY_HTTP!" echo jar_path = os.environ["CF_UPLOAD_FILE"]
>> "!PY_HTTP!" echo resp_path = os.environ.get("CF_RESP_FILE", "")
>> "!PY_HTTP!" echo project_id = os.environ["CURSEFORGE_PROJECT_ID"]
>> "!PY_HTTP!" echo token = os.environ["CURSEFORGE_TOKEN"]
>> "!PY_HTTP!" echo boundary = "----cfboundary" + uuid.uuid4().hex
>> "!PY_HTTP!" echo def field(name, content, ctype=None):
>> "!PY_HTTP!" echo ^    return (f"--{boundary}\\r\\n"
>> "!PY_HTTP!" echo ^            f"Content-Disposition: form-data; name=\\\"{name}\\\"\\r\\n"
>> "!PY_HTTP!" echo ^            + (f"Content-Type: {ctype}\\r\\n" if ctype else "")
>> "!PY_HTTP!" echo ^            + "\\r\\n").encode() + content + b"\\r\\n"
>> "!PY_HTTP!" echo def file_part(name, filename, content, ctype):
>> "!PY_HTTP!" echo ^    return (f"--{boundary}\\r\\n"
>> "!PY_HTTP!" echo ^            f"Content-Disposition: form-data; name=\\\"{name}\\\"; filename=\\\"{filename}\\\"\\r\\n"
>> "!PY_HTTP!" echo ^            f"Content-Type: {ctype}\\r\\n\\r\\n").encode() + content + b"\\r\\n"
>> "!PY_HTTP!" echo with open(meta_path, "rb") as fh:
>> "!PY_HTTP!" echo ^    meta_bytes = fh.read()
>> "!PY_HTTP!" echo with open(jar_path, "rb") as fh:
>> "!PY_HTTP!" echo ^    jar_bytes = fh.read()
>> "!PY_HTTP!" echo body = b"".join([
>> "!PY_HTTP!" echo ^    field("metadata", meta_bytes, "application/json"),
>> "!PY_HTTP!" echo ^    file_part("file", os.path.basename(jar_path), jar_bytes, "application/java-archive"),
>> "!PY_HTTP!" echo ^    f"--{boundary}--\\r\\n".encode()
>> "!PY_HTTP!" echo ])
>> "!PY_HTTP!" echo url = f"https://minecraft.curseforge.com/api/projects/{project_id}/upload-file"
>> "!PY_HTTP!" echo headers = {"X-Api-Token": token, "Content-Type": f"multipart/form-data; boundary={boundary}"}
>> "!PY_HTTP!" echo req = urllib.request.Request(url, data=body, headers=headers, method="POST")
>> "!PY_HTTP!" echo status = 0
>> "!PY_HTTP!" echo resp_body = b""
>> "!PY_HTTP!" echo try:
>> "!PY_HTTP!" echo ^    with urllib.request.urlopen(req) as resp:
>> "!PY_HTTP!" echo ^        status = resp.getcode()
>> "!PY_HTTP!" echo ^        resp_body = resp.read()
>> "!PY_HTTP!" echo except urllib.error.HTTPError as exc:
>> "!PY_HTTP!" echo ^    status = getattr(exc, "code", 0)
>> "!PY_HTTP!" echo ^    resp_body = exc.read()
>> "!PY_HTTP!" echo except Exception as exc:
>> "!PY_HTTP!" echo ^    sys.stderr.write(str(exc))
>> "!PY_HTTP!" echo ^    sys.exit(2)
>> "!PY_HTTP!" echo if resp_path:
>> "!PY_HTTP!" echo ^    try:
>> "!PY_HTTP!" echo ^        open(resp_path, "wb").write(resp_body)
>> "!PY_HTTP!" echo ^    except Exception:
>> "!PY_HTTP!" echo ^        pass
>> "!PY_HTTP!" echo print(status)
>> "!PY_HTTP!" echo sys.exit(0 if status in (200, 201) else 1)
for /f %%H in ('python "!PY_HTTP!"') do set "CF_HTTP_STATUS=%%H"
set "PY_RC=%ERRORLEVEL%"
del "!PY_HTTP!" >nul 2>&1
if not defined CF_HTTP_STATUS set "CF_HTTP_STATUS="
exit /b %PY_RC%

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
