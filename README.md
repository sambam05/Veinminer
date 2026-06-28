# Veinminer

Veinminer is a configurable Minecraft mining mod that lets players break connected veins with one normal block break. The current default experience is intentionally simple: install the mod, hold Shift while mining, and vein mining works without running a setup command.

The optional client mod adds a keybind, but the server-side default activation is Shift/sneak so the mod works out of the box on servers and in single-player.

## Repository Architecture

- `VMM/` contains the active shared implementation.
- Top-level `VMM-fabric-*` and `VMM-neoforge-*` folders are the live build modules.
- `VMM/core/*/fabric` and `VMM/core/*/neoforge` are wired into active Gradle source sets.
- `VMM/core/*/shared` currently contains placeholder/shared-looking sources and is not wired into active builds.
- `archive/legacy-shared/` is reference-only archive content.
- `scripts/` contains build and release automation.
- `config/` contains repository metadata and template config files.
- `docs/` contains changelog, command docs, publishing text, and repo snapshots.
- `artifacts/` contains generated outputs such as `dist-*` and build logs.
- `secrets/` is local-only and gitignored.

Wrapper modules should stay thin. Put gameplay logic in `VMM/core/*`, not in loader/version wrappers.

## Key Features

- Out-of-box vein mining for normal players.
- Shift/sneak activation by default.
- Connected block scanning with safe block limits.
- Tool and block list controls for server owners.
- Optional durability protection.
- Optional particles, cooldown, hunger/exhaustion, LuckPerms integration, and advanced test/admin tools.
- Advanced commands are hidden unless `advanced.enabled = true`.
- No `/veinminer setup` step is required.

## Basic Usage

1. Install the correct Fabric or NeoForge jar.
2. Start the game/server.
3. Hold Shift while mining a supported ore/log block.
4. Use `/veinminer toggle` only if you want to disable or re-enable the feature for yourself.

Normal players do not need commands for the default mining flow, but per-user preferences are available under `/veinminer`.

## Commands

Default commands:

| Command | Description |
| --- | --- |
| `/veinminer` | Show quick help. |
| `/veinminer help [topic]` | Show help for `toggle`, `reload`, `activation`, `togglemessages`, or `particles`. |
| `/veinminer toggle` | Toggle Veinminer for yourself. |
| `/veinminer activation keybind` | Toggle your activation input between Shift and the optional client keybind. |
| `/veinminer activation mode hold\|toggle` | Set your activation mode. |
| `/veinminer togglemessages <permission\|disabled\|cooldown\|durability>` | Toggle your own feedback messages. |
| `/veinminer particles toggle` | Toggle your own outline particles. |
| `/veinminer particles setcolor <red> <green> <blue>` | Set your own outline particle color. |
| `/veinminer particles setduration <ticks>` | Set your own outline particle duration. |
| `/veinminer reload` | Reload configs from disk. Requires admin/manage permission. |

Admin commands are always registered under `/vmadmin`:

| Command area | Description |
| --- | --- |
| `/vmadmin blocks ...` | Manage global block lists. |
| `/vmadmin tools ...` | Manage allowed tools. |
| `/vmadmin settings ...` | Manage block list mode, max blocks, cooldown, exhaustion, and LuckPerms settings. |
| `/vmadmin reload` | Reload configs from disk. |
| `/vmadmin confirm` | Confirm a pending destructive admin action. |
| `/vmadmin cancel` | Cancel a pending destructive admin action. |

Advanced commands are registered only when `advanced.enabled = true` in `GeneralConfig.toml`:

| Command area | Description |
| --- | --- |
| `/vmadvanced blockpertool ...` | Manage per-tool block lists. |
| `/vmadvanced settings blockpertool` | Toggle per-tool block list mode. |
| `/vmadvanced test` | Run the advanced feature test harness. |
| `/vmadvanced confirm` | Confirm a pending destructive admin action. |
| `/vmadvanced cancel` | Cancel a pending destructive admin action. |

## Config Files

Generated under `config/Veinminer/` on first run:

- `GeneralConfig.toml` - basic defaults, visual settings, advanced gates, block limits, cooldown, exhaustion, and integration options.
- `ToolsList.toml` - allowed tools, including optional `hand` support.
- `BlocksList.toml` - global block list.
- `BlockPerToolList.toml` - per-tool block lists when per-tool mode is enabled.

Important defaults:

```toml
basic.enabled = true
basic.activation_key = "SHIFT"
basic.require_correct_tool = true
basic.check_tool_durability = true
basic.durability_cap = 1
basic.durability_mode = "ABSOLUTE"

visual.show_particles = true
visual.show_chat_feedback = true

advanced.enabled = false
advanced.debug_logging = false
advanced.block_limits.max_blocks = 64
advanced.cooldown.enabled = false
advanced.exhaustion.enabled = false
```

These defaults are intended to work immediately after install.

## Permissions

General:

- `veinminer.use`
- `veinminer.reload`
- `veinminer.settings.manage`
- `veinminer.*`

Blocks:

- `veinminer.blocks.manage`
- `veinminer.blocks.add`
- `veinminer.blocks.remove`
- `veinminer.blocks.list`
- `veinminer.blocks.*`

Tools:

- `veinminer.tools.manage`
- `veinminer.tools.add`
- `veinminer.tools.remove`
- `veinminer.tools.list`
- `veinminer.tools.*`

Per-tool:

- `veinminer.blockpertool.manage`
- `veinminer.blockpertool.blocks.add|remove|list`
- `veinminer.blockpertool.tools.add|remove|list`
- `veinminer.blockpertool.blocks.*`
- `veinminer.blockpertool.tools.*`

Particles:

- `veinminer.particles.manage`
- `veinminer.particles.enable`
- `veinminer.particles.disable`
- `veinminer.particles.setcolor`
- `veinminer.particles.setduration`

## Compatibility

- Fabric: Minecraft 1.20 through 1.21.11.
- NeoForge: Minecraft 1.21 through 1.21.11.
- Dedicated servers and single-player integrated servers.
- No Forge builds are currently planned.

## Client Optional

Installing the client build enables the keybind and sends key state to the server. Default key: `V`.

Without the client, Shift/sneak activation remains available and is the default.

## Installation

1. Download the latest build for your loader and Minecraft version.
2. Drop the jar into `/mods`.
3. Restart the server or client.
4. Play normally. Edit configs or enable advanced mode only if you need admin controls.

## Support

Discord: https://discord.gg/43nu6wSWRC

## Other Projects

- Hammer Mining: https://modrinth.com/mod/hammer-mining-enchantment
