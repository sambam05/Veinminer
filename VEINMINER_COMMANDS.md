# Veinminer Command & Feature Guide (all supported versions)
Applies to every supported Fabric and NeoForge build (currently Minecraft 1.20 through 1.21.10). All commands run in-game with `/veinminer ...`. Unless noted, options apply to both loaders.

## Quick Reference
- `/veinminer` - prints the overview help text in chat.
- `/veinminer help [topic]` - print a specific help page. Topics: `overview`, `blocks`, `blockpertool`, `tools`, `settings`, `setup`, `blocklistmode`, `activation`, `togglemessages`, `particles`, `reload`, `toggle`.

## Player Controls (no special permission)
- `/veinminer toggle` — flips your personal Veinminer on/off. Stored per-player.
- `/veinminer toggleparticles` — flips particles for you only (server-wide setting still applies).
- `/veinminer togglemessages` - shows message toggle help.
- `/veinminer togglemessages <permission|disabled|cooldown|durability>` - enables/disables that message category for you.
- `/veinminer activation help` - shows activation help text.
- `/veinminer activation keybind enable|disable` - allows or blocks the keybind from triggering vein-mining (only shown when the client mod is installed); also clears the key-toggle state when changed.
- `/veinminer activation mode hold|toggle` - hold: activation only works while the key/crouch is held; toggle: tap once to latch the activation state for keybinds and crouch.

## Manager/Admin Controls
Require the manage permission (op level 2+ or `veinminer.reload` via your permission system).

- `/veinminer reload` - reloads config files from disk and reapplies to the running server. Reports success or failure with details.

### Setup Wizard
- `/veinminer setup` - starts the setup wizard if you don't have a session, otherwise re-shows the current step.
- `/veinminer setup start` - starts a new setup session for you.
- `/veinminer setup show` - reprint the current step and buttons.
- `/veinminer setup next` / `/veinminer setup back` - navigation between steps.
- `/veinminer setup status` - prints the current draft summary.
- `/veinminer setup set <key> <value>` - manually set a draft value (also used by the buttons).
- `/veinminer setup apply` - saves the draft to `GeneralConfig.toml` and reloads config live.
- `/veinminer setup cancel` - cancels your setup session without saving.

### Global Block List
- `/veinminer blocks help` - prints usage for the global list.
- `/veinminer blocks` or `/veinminer blocks list` - lists the current global entries and shows whether they are treated as a whitelist or blacklist. Hints when per-tool mode is active.
- `/veinminer blocks add <block_id>` — adds the block to the global list; errors if invalid or already present; applies immediately.
- `/veinminer blocks remove <block_id>` — removes the block from the global list; errors if not present; applies immediately.

### Per-Tool Block Lists (only when block list mode is per-tool)
- `/veinminer blockpertool help` — prints usage for per-tool lists.
- `/veinminer blockpertool blocks <tool> list` — lists blocks assigned to a tool key under the current per-tool mode (whitelist/blacklist semantics).
- `/veinminer blockpertool blocks <tool> add <block_id>` — adds a block entry for that tool; reloads live; errors on invalid/duplicate.
- `/veinminer blockpertool blocks <tool> remove <block_id>` — removes a block entry for that tool; errors if missing.
- `/veinminer blockpertool tool list` — shows all tools that have per-tool block entries.
- `/veinminer blockpertool tool add <tool>` — registers a tool key (e.g., `minecraft:iron_pickaxe` or `hand`) so it can hold per-tool block lists.
- `/veinminer blockpertool tool remove <tool>` — deletes the tool key and its per-tool block entries.

### Tools List
- `/veinminer tools help` — prints usage for the tools list.
- `/veinminer tools` or `/veinminer tools list` — lists globally allowed tools; `hand` means empty-hand activation is allowed.
- `/veinminer tools add <item_id|hand>` — adds a tool; errors on invalid/duplicate; applies immediately.
- `/veinminer tools remove <item_id|hand>` — removes a tool; errors if missing; applies immediately.

### Settings
- `/veinminer settings` - prints current values: block list mode, max blocks, cooldown enabled flag, cooldown time, hunger exhaustion enabled flag, hunger exhaustion scale.
- `/veinminer settings blockpertool` - flips between global and per-tool block list modes while preserving whitelist/blacklist state.
- `/veinminer settings blocklistmode <whitelist|blacklist>` - switches the block list type while keeping the current global vs. per-tool scope.
- `/veinminer settings cooldown enable|disable` - turns the vein-mining cooldown on/off; saves and reloads live.
- `/veinminer settings cooldown set <seconds>` - sets cooldown length; saves and reloads live.
- `/veinminer settings exhaustion enable|disable` - enables/disables hunger exhaustion applied by Veinminer; saves and reloads live.
- `/veinminer settings exhaustion scale <value>` - sets the hunger exhaustion multiplier (1.0 = vanilla per block; lower/greater scale accordingly); saves and reloads live.
- `/veinminer settings luckperms enable|disable` - enables/disables automatic LuckPerms integration; saves and reloads live.
- `/veinminer settings maxblocks <value>` - sets the max blocks Veinminer will break in one operation; saves and reloads live.

### Particles (server-wide)
- `/veinminer particles help` - prints usage for particle settings.
- `/veinminer particles toggle` - toggles particles for all players globally and reports the new state.
- `/veinminer particles setcolor <red> <green> <blue>` — sets RGB color (0-255 each); applies server-wide.
- `/veinminer particles setduration <ticks>` — sets particle lifetime in ticks; applies server-wide.

### Test Harness
- `/veinminer test` — runs the built-in feature test harness (admin-only). Fails with a "busy" message if a run is already in progress; reports pass/fail per scripted step.

## Permissions
- `veinminer.use` — checked for normal vein-mining use in code.
- `veinminer.reload` — gates admin/manage commands (reload, settings, list edits, particles, test).

## Block List Modes (behavior summary)
- **Whitelist**: only listed blocks are vein-mined.
- **Blacklist**: all blocks are vein-mined except listed ones.
- **Per-tool modes**: same rules, but lists are scoped per tool; requires per-tool mode to use the `/blockpertool` commands.
