## 3.1.0
### **New:**
- Optional exhaustion cost for vein mining (disabled by default) with `Exhaustion.enabled` and `Exhaustion.scale` in `GeneralConfig.toml` (`1.0` = vanilla per-block cost).
- Admin commands for exhaustion: `/veinminer settings exhaustion enable|disable`, `/veinminer settings exhaustion scale <value>`.
- Vein-mined blocks now increment vanilla mined-block stats (Fabric uses `Stats.MINED`, NeoForge uses `Stats.BLOCK_MINED`) so external stat trackers reflect vein mining.
- Interactive `/veinminer setup` wizard (admin-only) with clickable chat buttons covering enable/disable, crouch requirement, block limits, cooldown, exhaustion + scale, durability guard, particles, and block list mode.
- Admin login prompt (clickable Yes/No) offering to run `/veinminer setup` until dismissed or accepted.

### **Changes:**
- `/veinminer settings` output now shows exhaustion enabled + scale.
- Added `/veinminer help setup` topic entry.
- Setup wizard UI restyled (colored headers/buttons, spaced nav row) and clears chat between steps; durability guard and particles now use multi-step flows (enable -> mode/value and enable -> duration/color).
- Block list mode buttons/command remain `whitelist`/`blacklist`, but now honor the separate block-per-tool toggle: config writes `blockListMode = "WHITELIST|BLACKLIST"` plus `blocksPerTool = true|false` and still loads legacy `GLOBAL_*`/`PER_TOOL_*` values.

### **Fixes:**
- N/A
