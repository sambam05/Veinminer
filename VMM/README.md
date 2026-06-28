# VMM Modular Layout

This folder contains the refactored multi-loader structure.

## Core

Shared gameplay systems are grouped by feature area:

- `core/basic`
- `core/visual`
- `core/advanced`
- `core/commands`
- `core/config`
- `core/util`

Each feature area contains loader-specific source trees (`fabric`, `neoforge`) so version projects can share one maintained core per loader.

## Loader Folders

Requested loader/version entrypoint layout is provided under:

- `fabric/<mc-version>/.../FabricEntrypoint.java`
- `neoforge/<mc-version>/.../NeoForgeEntrypoint.java`

These entrypoint classes are placeholders for migration and are currently not wired into active wrapper builds.

Current active build entrypoints still come from the top-level wrapper modules (for example `VMM-fabric-v*` and `VMM-neoforge-v*` folders).

Version wrapper projects consume shared core code via `sourceSets` in each wrapper `build.gradle`.
