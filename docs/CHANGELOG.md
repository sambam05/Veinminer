## 3.1.3
### Supported Builds
- Built and packaged Fabric jars for Minecraft `1.20` through `1.21.11`.
- Built and packaged NeoForge jars for Minecraft `1.21` through `1.21.11`.
- Aligned release identity on `3.1.3` across Gradle metadata, generated jar names, and release documentation.

### Changed
- Streamlined the default player flow: Veinminer now works out of the box with Shift/sneak activation and no setup wizard.
- Removed `/veinminer setup` from the documented normal flow; normal users can install the mod and mine supported blocks immediately.
- Reworked the command layout:
  - `/veinminer` now owns normal player-facing controls such as toggle, activation mode, message preferences, and particle preferences.
  - `/vmadmin` now owns always-registered admin/server controls for blocks, tools, settings, reload, confirm, and cancel.
  - `/vmadvanced` remains hidden unless `advanced.enabled = true` and contains advanced-only block-per-tool and test tooling.
- Made particle preferences per-player instead of treating all particle toggle/color/duration changes as global server settings.
- Standardized per-player preference storage for both loaders around `config/Veinminer/Players/PlayerData.json`.
- Preserved transient activation state separately from saved preferences so toggle/key state cleanup does not erase persisted settings.
- Updated README, command documentation, publishing descriptions, and release checklist text to match the streamlined command flow.

### Fixed
- Fixed Fabric per-player settings persistence by restoring JSON-backed player preference storage across supported Fabric versions.
- Fixed Fabric saved preferences being overwritten after single-player disconnect/server shutdown.
- Fixed Fabric `1.21.5`-`1.21.11` launch crashes caused by player-data persistence mixin callback descriptors.
- Fixed NeoForge new-player defaults so Veinminer, particles, red particle color, and message preferences start enabled as intended.
- Fixed NeoForge `1.21.10` and `1.21.11` launch crashes caused by player-data persistence mixin callback descriptors.
- Fixed NeoForge particle/message/Veinminer defaults appearing disabled or black on fresh profiles.
- Fixed `advanced.enabled = true` startup failures caused by the `/vmadvanced test` harness reflecting an outdated block-limit helper signature.
- Historical: fixed setup wizard chat text not being clickable on Fabric `1.21.5`-`1.21.11`.
- Historical: fixed setup wizard chat text not being clickable on NeoForge `1.21.5`-`1.21.11`.

### Validation
- Full Fabric build matrix passed and produced 19 jars.
- Full NeoForge build matrix passed and produced 12 jars.
- Runtime smoke testing confirmed Fabric settings persistence and basic behaviour on `1.20.4`, `1.20.6`, `1.21.1`, `1.21.4`, `1.21.8`, `1.21.10`, and `1.21.11`.
- Runtime smoke testing confirmed NeoForge defaults, settings persistence, and clean launch on `1.21.1`, `1.21.4`, `1.21.8`, `1.21.10`, and `1.21.11`.
- Runtime smoke testing confirmed `advanced.enabled = true` no longer blocks world load or command registration across the tested Fabric and NeoForge release matrix.
