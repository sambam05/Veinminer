# Veinminer Release Checklist

This checklist is for the `3.1.3` release. Runtime smoke testing has now passed for the supported Fabric and NeoForge release matrix; publishing steps remain separate.

## Version Identity

- [x] Active Gradle `mod_version` values are `3.1.3`.
- [x] Changelog has a single `3.1.3` section for the current release notes.
- [x] Fresh generated jars use `3.1.3` in their file names.
- [ ] Release tag is created from the final reviewed commit.
- [ ] Modrinth and CurseForge release notes match `docs/CHANGELOG.md`.

## Build Validation

- [x] Run `scripts\build-fabric.bat`.
- [x] Run `scripts\build-neoforge.bat`.
- [x] Confirm Fabric jars are generated for Minecraft `1.20` through `1.21.11`.
- [x] Confirm NeoForge jars are generated for Minecraft `1.21` through `1.21.11`.
- [x] Confirm no generated build cache files are left as untracked source candidates.

## Fabric Smoke Test

- [x] Start a clean Fabric server or single-player instance with the matching jar.
- [x] Confirm the mod loads with no startup errors.
- [x] Join as a normal player with default generated config.
- [x] Mine a supported ore/log while holding Shift/sneak and confirm connected blocks break.
- [x] Confirm normal single-block mining still works when Shift/sneak is not held.
- [x] Run `/veinminer` and `/veinminer help`.
- [x] Test `/veinminer toggle`.
- [x] Test `/veinminer activation keybind` and `/veinminer activation mode hold|toggle`.
- [x] Test `/veinminer togglemessages permission|disabled|cooldown|durability`.
- [x] Test `/veinminer particles toggle`, `setcolor`, and `setduration`.
- [x] Relog or restart and confirm per-player settings persist.
- [x] As an admin, test `/vmadmin blocks`, `/vmadmin tools`, `/vmadmin settings`, and `/vmadmin reload`.
- [x] With `advanced.enabled = false`, confirm `/vmadvanced` is absent or unavailable.
- [x] Set `advanced.enabled = true`, restart or reload as required, and test `/vmadvanced blockpertool`, `/vmadvanced settings blockpertool`, and `/vmadvanced test`.
- [x] Confirm config files load from `config/Veinminer/`.
- [x] If a permissions plugin is installed, confirm allowed and denied users behave correctly.

## NeoForge Smoke Test

- [x] Start a clean NeoForge server or single-player instance with the matching jar.
- [x] Confirm the mod loads with no startup errors.
- [x] Join as a normal player with default generated config.
- [x] Mine a supported ore/log while holding Shift/sneak and confirm connected blocks break.
- [x] Confirm normal single-block mining still works when Shift/sneak is not held.
- [x] Run `/veinminer` and `/veinminer help`.
- [x] Test `/veinminer toggle`.
- [x] Test `/veinminer activation keybind` and `/veinminer activation mode hold|toggle`.
- [x] Test `/veinminer togglemessages permission|disabled|cooldown|durability`.
- [x] Test `/veinminer particles toggle`, `setcolor`, and `setduration`.
- [x] Relog or restart and confirm per-player settings persist.
- [x] As an admin, test `/vmadmin blocks`, `/vmadmin tools`, `/vmadmin settings`, and `/vmadmin reload`.
- [x] With `advanced.enabled = false`, confirm `/vmadvanced` is absent or unavailable.
- [x] Set `advanced.enabled = true`, restart or reload as required, and test `/vmadvanced blockpertool`, `/vmadvanced settings blockpertool`, and `/vmadvanced test`.
- [x] Confirm config files load from `config/Veinminer/`.
- [x] If LuckPerms is installed, confirm allowed and denied users behave correctly.

## Release Classification

- Build-clean only: build matrix passes, but live smoke tests are incomplete.
- Release candidate: build matrix passes and docs/versioning are aligned, but live smoke tests are still pending.
- Stable: build matrix passes and both Fabric and NeoForge live smoke tests pass.

Current classification: stable for the `3.1.3` supported build matrix, pending release tag creation and marketplace publishing.
