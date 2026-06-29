## 3.1.3
### Changed
- Streamlined the documented default flow: Veinminer works out of the box with Shift/sneak activation.
- Documented that `/veinminer setup` is no longer required for normal use.
- Documented advanced/admin command areas under `/vmadvanced` when `advanced.enabled = true`.

### Fixed
- Fixed Fabric per-player settings persistence by restoring JSON-backed player preference storage across supported Fabric versions.
- Fixed Fabric `1.21.5`-`1.21.11` launch crashes caused by player-data persistence mixin callback descriptors.
- Historical: fixed setup wizard chat text not being clickable on Fabric `1.21.5`-`1.21.11`.
- Historical: fixed setup wizard chat text not being clickable on NeoForge `1.21.5`-`1.21.11`.
