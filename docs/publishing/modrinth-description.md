# Veinminer

Veinminer lets players break connected veins with one normal block break. It is designed to work immediately after install: hold Shift while mining a supported block and the vein is mined automatically.

No `/veinminer setup` command is required.

## Key Features

- Works out of the box with Shift/sneak activation.
- Server-side basic flow; optional client keybind support.
- Connected ore/log vein mining with safe block limits.
- Tool and block list controls for server owners.
- Optional durability protection.
- Optional particles, cooldown, hunger/exhaustion, and LuckPerms integration.
- Advanced/admin commands are hidden unless `advanced.enabled = true`.

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

Advanced commands are only registered when `advanced.enabled = true`:

| Command area | Description |
| --- | --- |
| `/vmadvanced blockpertool ...` | Manage per-tool block lists. |
| `/vmadvanced settings blockpertool` | Toggle per-tool block list mode. |
| `/vmadvanced test` | Run the advanced feature test harness. |

## Config

Generated under `config/Veinminer/`:

- `GeneralConfig.toml`
- `ToolsList.toml`
- `BlocksList.toml`
- `BlockPerToolList.toml`

Important defaults:

```toml
basic.enabled = true
basic.activation_key = "SHIFT"
basic.require_correct_tool = true
visual.show_particles = true
advanced.enabled = false
advanced.debug_logging = false
advanced.block_limits.max_blocks = 64
```

## Compatibility

- Fabric: Minecraft 1.20 through 1.21.11.
- NeoForge: Minecraft 1.21 through 1.21.11.
- Dedicated servers and single-player integrated servers.
- No Forge builds are currently planned.

## Client Optional

Installing the client build enables the keybind and sends key state to the server. Default key: `V`.

Without the client, Shift/sneak activation remains available and is the default.

## Support

Discord: https://discord.gg/43nu6wSWRC
