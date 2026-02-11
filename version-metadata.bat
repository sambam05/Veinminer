@echo off
setlocal EnableExtensions

REM Single source of truth for supported projects and Minecraft versions.
REM Usage:
REM   call "%~dp0version-metadata.bat" build
REM   call "%~dp0version-metadata.bat" upload
REM   call "%~dp0version-metadata.bat" all

set "SCOPE=%~1"
if "%SCOPE%"=="" set "SCOPE=all"

if /I "%SCOPE%"=="build" goto :emitBuild
if /I "%SCOPE%"=="upload" goto :emitUpload
if /I "%SCOPE%"=="all" goto :emitAll

echo [error] Unknown metadata scope "%~1". Use: build, upload, or all.
exit /b 1

:emitBuild
endlocal & (
    REM NeoForge 1.20.x builds were dropped (no moddev bundle).
    set "NFO_LEGACY_PROJECTS=VMM-neoforge-v1.20-v1.20.4 VMM-neoforge-v1.20.5-v1.20.6"
    set "NFO_PROJECTS=VMM-neoforge-v1.21-v1.21.1 VMM-neoforge-v1.21.2-v1.21.4 VMM-neoforge-v1.21.5-v1.21.8 VMM-neoforge-v1.21.9-v1.21.10 VMM-neoforge-v1.21.11"
    set "NFO_ALL_PROJECTS=VMM-neoforge-v1.20-v1.20.4 VMM-neoforge-v1.20.5-v1.20.6 VMM-neoforge-v1.21-v1.21.1 VMM-neoforge-v1.21.2-v1.21.4 VMM-neoforge-v1.21.5-v1.21.8 VMM-neoforge-v1.21.9-v1.21.10 VMM-neoforge-v1.21.11"
    REM Keep Fabric 1.20 through 1.21.11.
    set "FAB_PROJECTS=VMM-fabric-v1.20-v1.20.4 VMM-fabric-v1.20.5-v1.20.6 VMM-fabric-v1.21-v1.21.1 VMM-fabric-v1.21.2-v1.21.4 VMM-fabric-v1.21.5-v1.21.8 VMM-fabric-v1.21.9-v1.21.10 VMM-fabric-v1.21.11"
    set "FAB_ALL_PROJECTS=VMM-fabric-v1.20-v1.20.4 VMM-fabric-v1.20.5-v1.20.6 VMM-fabric-v1.21-v1.21.1 VMM-fabric-v1.21.2-v1.21.4 VMM-fabric-v1.21.5-v1.21.8 VMM-fabric-v1.21.9-v1.21.10 VMM-fabric-v1.21.11"
)
exit /b 0

:emitUpload
endlocal & (
    set "NEO_LEGACY_VERSIONS=1.20 1.20.1 1.20.2 1.20.3 1.20.4 1.20.5 1.20.6"
    set "NEO_VERSIONS=1.21 1.21.1 1.21.2 1.21.3 1.21.4 1.21.5 1.21.6 1.21.7 1.21.8 1.21.9 1.21.10 1.21.11"
    set "NEO_ALL_VERSIONS=1.20 1.20.1 1.20.2 1.20.3 1.20.4 1.20.5 1.20.6 1.21 1.21.1 1.21.2 1.21.3 1.21.4 1.21.5 1.21.6 1.21.7 1.21.8 1.21.9 1.21.10 1.21.11"
    set "FAB_VERSIONS=1.20 1.20.1 1.20.2 1.20.3 1.20.4 1.20.5 1.20.6 1.21 1.21.1 1.21.2 1.21.3 1.21.4 1.21.5 1.21.6 1.21.7 1.21.8 1.21.9 1.21.10 1.21.11"
    set "FAB_ALL_VERSIONS=1.20 1.20.1 1.20.2 1.20.3 1.20.4 1.20.5 1.20.6 1.21 1.21.1 1.21.2 1.21.3 1.21.4 1.21.5 1.21.6 1.21.7 1.21.8 1.21.9 1.21.10 1.21.11"
)
exit /b 0

:emitAll
call "%~f0" build
if errorlevel 1 exit /b 1
call "%~f0" upload
if errorlevel 1 exit /b 1
exit /b 0
