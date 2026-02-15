## Shared Source Sets

This directory centralizes Java sources that were duplicated across legacy target folders.

### Layout

- `shared/fabric-legacy/src/main/java`
- `shared/neoforge-legacy/src/main/java`

### Who uses these

Fabric legacy projects:

- `VMM-fabric-v1.20-v1.20.4`
- `VMM-fabric-v1.20.5-v1.20.6`
- `VMM-fabric-v1.21-v1.21.1`
- `VMM-fabric-v1.21.2-v1.21.4`
- `VMM-fabric-v1.21.5-v1.21.8`
- `VMM-fabric-v1.21.9-v1.21.10`

NeoForge legacy projects:

- `VMM-neoforge-v1.20-v1.20.4`
- `VMM-neoforge-v1.20.5-v1.20.6`
- `VMM-neoforge-v1.21-v1.21.1`
- `VMM-neoforge-v1.21.2-v1.21.4`
- `VMM-neoforge-v1.21.5-v1.21.8`
- `VMM-neoforge-v1.21.9-v1.21.10`

Each of those `build.gradle` files adds a shared Java source dir via:

- `sourceSets.main.java.srcDir '../shared/fabric-legacy/src/main/java'`
- `sourceSets.main.java.srcDir '../shared/neoforge-legacy/src/main/java'`

### Maintenance rule

- If a change is common to all legacy Fabric versions, edit `shared/fabric-legacy`.
- If a change is common to all legacy NeoForge versions, edit `shared/neoforge-legacy`.
- Keep version-specific or loader-specific adapters in each project's local `src/main/java`.
