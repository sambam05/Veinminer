## 3.1.1
### **New:**

### **Changes:**
- Added copper pickaxe support to the default veinmining tool list for Minecraft 1.21.11 (Fabric and NeoForge).
- Re-ran the full multi-version build pipeline (`build-all.bat`) and verified successful Fabric/NeoForge artifacts across supported targets.

### **Fixes:**
- Fixed unsafe off-thread world access during vein detection by moving block-state traversal to the server thread before async planning.
- Added explicit async failure handling for vein planning (`exceptionally`) with error logging and a single-block fallback so canceled breaks do not silently fail.
- Prevented per-player settings loss on disconnect by flushing player settings before in-memory state is dropped.
- Added automated test coverage for config formatting/parsing behavior, rule matching, cooldown rounding, and durability cap logic.
- Added GitHub Actions CI matrix to run tests for both Fabric and NeoForge targets.
- Fixed the controls menu keybind category translation for identifier-based key categories by adding `key.categories.veinminermod.veinminer` in 1.21.9-1.21.10 and 1.21.11 (Fabric and NeoForge).
