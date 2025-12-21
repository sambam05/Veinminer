# ⛏ Veinminer

Veinminer is a configurable mining mod that lets you break entire veins with a single block break. It keeps the grind down while giving server owners clear controls, limits, and permissions. Works server-side out of the box; the optional client mod adds a keybind (default V).

---

## ⚙️ Key Features

- Vein Mining — mines adjacent matching blocks with TPS-aware block caps.
- Activation Choices — crouch or hotkey; hold or toggle modes per player.
- Empty-Hand Support — trigger without holding a tool (optional).
- Flexible Block Lists — global and per-tool lists can be whitelist or blacklist.
- Per-Tool Rules — different block sets per tool; seeded defaults.
- Fortune/Silk & Durability Safety — honors enchants; optional durability guard.
- Cooldown & Limits — optional cooldown; static or dynamic max blocks.
- Particles/Outline — server-driven outline with color + duration controls.
- Hunger/Exhaustion Cost — optional hunger exhaustion with a configurable multiplier (off by default).
- Setup Wizard + Login Prompt — admin-only clickable wizard; admins are prompted on join until they click Yes/No (then never again).
- LuckPerms Toggle — optional integration for permissions.
- `/veinminer test` — quick command to verify behavior and responses.
- Live Reloads — `/veinminer reload` with file/line reporting on errors.

---

## 🔧 Configuration & Commands

### Command quick reference

| Command | Description |
| --- | --- |
| `/veinminer` | Show overview help. |
| `/veinminer help [topic]` | Show a specific help page. |
| `/veinminer toggle` | Toggle Veinminer for yourself (per-player). |
| `/veinminer toggleparticles` | Toggle particles for you (per-player). |
| `/veinminer togglemessages <permission\|disabled\|cooldown\|durability>` | Toggle specific chat notifications (per-player). |
| `/veinminer activation mode hold\|toggle` | Choose hold vs toggle activation (per-player). |
| `/veinminer activation keybind enable\|disable` | Allow/deny keybind activation (client required). |
| `/veinminer blocks add/remove/list/clear <block_id\|#tag>` | Manage the global block list (admin/manage). |
| `/veinminer tools add/remove/list/clear <item_id\|#tag\|hand>` | Manage allowed tools (admin/manage). |
| `/veinminer blockpertool tool add/remove/list/clear <tool\|#tag\|hand>` | Manage tools in per-tool mode (admin/manage). |
| `/veinminer blockpertool blocks <tool> add/remove/list/clear <block_id\|#tag>` | Manage per-tool block lists (admin/manage). |
| `/veinminer setup` | Interactive setup wizard with clickable steps (admin/manage). |
| `/veinminer settings blockpertool` | Toggle per-tool block list mode (admin/manage). |
| `/veinminer settings blocklistmode whitelist\|blacklist` | Switch whitelist/blacklist behavior (admin/manage). |
| `/veinminer settings maxblocks <value>` | Set max blocks per activation (admin/manage). |
| `/veinminer settings cooldown enable\|disable` | Toggle cooldown (admin/manage). |
| `/veinminer settings cooldown set <seconds>` | Set cooldown duration (admin/manage). |
| `/veinminer settings exhaustion enable\|disable` | Toggle hunger exhaustion (admin/manage). |
| `/veinminer settings exhaustion scale <value>` | Set hunger exhaustion multiplier (1.0 = vanilla per block). |
| `/veinminer particles toggle` | Toggle outline particles globally (admin/manage). |
| `/veinminer particles setcolor <r> <g> <b>` | Set outline RGB color (admin/manage). |
| `/veinminer particles setduration <ticks>` | Set outline lifetime (admin/manage). |
| `/veinminer reload` | Reload configs from disk (admin/manage). |
| `/veinminer test` | Run the built-in feature test harness (admin/manage). |

### Config files

Written to `config/Veinminer/` (TOML) on first run.

- `GeneralConfig.toml` - feature toggles, activation rules, block limits, particles, cooldown, exhaustion, integration options.
- `ToolsList.toml` - which tools can veinmine (plus optional `hand`).
- `BlocksList.toml` - global block list (whitelist/blacklist depending on mode).
- `BlockPerToolList.toml` - per-tool block lists (used when per-tool mode is enabled).

<details>
<summary>GeneralConfig.toml (important keys)</summary>

```toml
[General]
veinminerEnabled = true
requireCrouch = true
checkToolDurability = true
durabilityCap = 1
durabilityThreshold = "ABSOLUTE"

[Cooldown]
enabled = false
seconds = 5

[BlockLimits]
dynamicMaxBlocks = false
maxBlocks = 64
minBlocks = 16
maxDynamicBlocks = 64

[Particles]
enabled = true
durationTicks = 60
red = 255
green = 0
blue = 0

[Exhaustion]
enabled = false
scale = 1.0
```
</details>

---

## 🛡️ Permissions

**General**
- `veinminer.use`
- `veinminer.reload`
- `veinminer.settings.manage`
- `veinminer.*`

**Blocks**
- `veinminer.blocks.manage`
- `veinminer.blocks.add`
- `veinminer.blocks.remove`
- `veinminer.blocks.list`
- `veinminer.blocks.*`

**Tools**
- `veinminer.tools.manage`
- `veinminer.tools.add`
- `veinminer.tools.remove`
- `veinminer.tools.list`
- `veinminer.tools.*`

**Per-Tool**
- `veinminer.blockpertool.manage`
- `veinminer.blockpertool.blocks.add|remove|list`
- `veinminer.blockpertool.tools.add|remove|list`
- `veinminer.blockpertool.blocks.*`, `veinminer.blockpertool.tools.*`

**Particles**
- `veinminer.particles.manage`
- `veinminer.particles.enable`, `veinminer.particles.disable`
- `veinminer.particles.setcolor`, `veinminer.particles.setduration`

---

## 🧱 Compatibility

- Minecraft 1.20.x - 1.21.x
- Fabric builds available
- NeoForge builds available
- Works on dedicated servers and single-player (integrated server)
- No Forge builds planned

---

## 🖥️ Client Optional

Installing the client build enables the hotkey and sends key state to the server. Default key: **V**.

Without the client, crouch-to-activate is available via `requireCrouch` for a vanilla-only experience.

---

## 📌 Planned

- 🌐 Translations  
- ✨ Veinminer enchantment 

---

## 🤔 Considering

- 🕹️ Client-side visual cues  
- 📊 Mining stats  

---

## 📥 Installation

1. Download the latest build for your platform.
2. Drop the jar in `/mods`.
3. Restart the server or client.
4. Configure with commands or edit the TOML files.

---

## 💬 Support

Discord: https://discord.gg/43nu6wSWRC

---

## 🧰 Other Projects

- [🔨 Hammer Mining](https://modrinth.com/mod/hammer-mining-enchantment) — 3×3 breaking via enchant.
