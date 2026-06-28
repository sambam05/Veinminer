# Veinminer Release Checklist

This checklist is for the `3.1.3` release candidate. Do not call the release stable until both the build matrix and the live smoke tests are complete.

## Version Identity

- [x] Active Gradle `mod_version` values are `3.1.3`.
- [x] Changelog has a single `3.1.3` section for the current release notes.
- [ ] Fresh generated jars use `3.1.3` in their file names.
- [ ] Release tag is created from the final reviewed commit.
- [ ] Modrinth and CurseForge release notes match `docs/CHANGELOG.md`.

## Build Validation

- [ ] Run `scripts\build-fabric.bat`.
- [ ] Run `scripts\build-neoforge.bat`.
- [ ] Confirm Fabric jars are generated for Minecraft `1.20` through `1.21.11`.
- [ ] Confirm NeoForge jars are generated for Minecraft `1.21` through `1.21.11`.
- [ ] Confirm no generated build cache files are left as untracked source candidates.

## Fabric Smoke Test

- [ ] Start a clean Fabric server or single-player instance with the matching jar.
- [ ] Confirm the mod loads with no startup errors.
- [ ] Join as a normal player with default generated config.
- [ ] Mine a supported ore/log while holding Shift/sneak and confirm connected blocks break.
- [ ] Confirm normal single-block mining still works when Shift/sneak is not held.
- [ ] Run `/veinminer` and `/veinminer help`.
- [ ] Test `/veinminer toggle`.
- [ ] Test `/veinminer activation keybind` and `/veinminer activation mode hold|toggle`.
- [ ] Test `/veinminer togglemessages permission|disabled|cooldown|durability`.
- [ ] Test `/veinminer particles toggle`, `setcolor`, and `setduration`.
- [ ] Relog or restart and confirm per-player settings persist.
- [ ] As an admin, test `/vmadmin blocks`, `/vmadmin tools`, `/vmadmin settings`, and `/vmadmin reload`.
- [ ] With `advanced.enabled = false`, confirm `/vmadvanced` is absent or unavailable.
- [ ] Set `advanced.enabled = true`, restart or reload as required, and test `/vmadvanced blockpertool`, `/vmadvanced settings blockpertool`, and `/vmadvanced test`.
- [ ] Confirm config files load from `config/Veinminer/`.
- [ ] If a permissions plugin is installed, confirm allowed and denied users behave correctly.

## NeoForge Smoke Test

- [ ] Start a clean NeoForge server or single-player instance with the matching jar.
- [ ] Confirm the mod loads with no startup errors.
- [ ] Join as a normal player with default generated config.
- [ ] Mine a supported ore/log while holding Shift/sneak and confirm connected blocks break.
- [ ] Confirm normal single-block mining still works when Shift/sneak is not held.
- [ ] Run `/veinminer` and `/veinminer help`.
- [ ] Test `/veinminer toggle`.
- [ ] Test `/veinminer activation keybind` and `/veinminer activation mode hold|toggle`.
- [ ] Test `/veinminer togglemessages permission|disabled|cooldown|durability`.
- [ ] Test `/veinminer particles toggle`, `setcolor`, and `setduration`.
- [ ] Relog or restart and confirm per-player settings persist.
- [ ] As an admin, test `/vmadmin blocks`, `/vmadmin tools`, `/vmadmin settings`, and `/vmadmin reload`.
- [ ] With `advanced.enabled = false`, confirm `/vmadvanced` is absent or unavailable.
- [ ] Set `advanced.enabled = true`, restart or reload as required, and test `/vmadvanced blockpertool`, `/vmadvanced settings blockpertool`, and `/vmadvanced test`.
- [ ] Confirm config files load from `config/Veinminer/`.
- [ ] If LuckPerms is installed, confirm allowed and denied users behave correctly.

## Release Classification

- Build-clean only: build matrix passes, but live smoke tests are incomplete.
- Release candidate: build matrix passes and docs/versioning are aligned, but live smoke tests are still pending.
- Stable: build matrix passes and both Fabric and NeoForge live smoke tests pass.
