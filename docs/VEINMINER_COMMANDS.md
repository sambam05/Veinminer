# Veinminer Command & Feature Guide (streamlined)
Applies to all maintained Fabric and NeoForge builds in this repository.

## Player Commands
These commands are registered under `/veinminer`:

- `/veinminer` - shows quick help.
- `/veinminer help [topic]` - help for `toggle`, `reload`, `activation`, `togglemessages`, or `particles`.
- `/veinminer toggle` - enable/disable Veinminer for yourself.
- `/veinminer reload` - reload configuration from disk (admin/manage).
- `/veinminer activation keybind` - toggle your activation input between Shift and the optional client keybind.
- `/veinminer activation mode hold|toggle` - set your activation mode.
- `/veinminer togglemessages permission|disabled|cooldown|durability` - toggle your own feedback messages.
- `/veinminer particles toggle` - toggle your own outline particles.
- `/veinminer particles setcolor <red> <green> <blue>` - set your own outline particle color.
- `/veinminer particles setduration <ticks>` - set your own outline particle duration.

## Admin Commands
These commands are always registered under `/vmadmin` and require admin/manage permission:

- `/vmadmin blocks add <id|#tag>`
- `/vmadmin blocks list`
- `/vmadmin blocks remove <id|#tag>`
- `/vmadmin blocks clear`
- `/vmadmin tools add <id|#tag|hand>`
- `/vmadmin tools list`
- `/vmadmin tools remove <id|#tag|hand>`
- `/vmadmin tools clear`
- `/vmadmin settings blocklistmode whitelist|blacklist`
- `/vmadmin settings maxblocks <value>`
- `/vmadmin settings cooldown enable|disable|set <seconds>`
- `/vmadmin settings exhaustion enable|disable|scale <value>`
- `/vmadmin settings luckperms enable|disable`
- `/vmadmin reload`
- `/vmadmin confirm`
- `/vmadmin cancel`

## Advanced Commands
These commands are isolated under `/vmadvanced` and are only registered when `advanced.enabled = true` in `GeneralConfig.toml`:

- `/vmadvanced blockpertool ...`
- `/vmadvanced settings blockpertool`
- `/vmadvanced test`
- `/vmadvanced confirm`
- `/vmadvanced cancel`

## Notes
- `/veinminer setup` is intentionally removed.
- Default activation behaviour is hold `SHIFT` while mining.
- The mod works out-of-box with default ore + log block coverage.
