# AGENTS.md: skillsjars-sbt-plugin

## Agent tooling

- Follow the `zen-of-projects` Skill (extract with `./sbta extractSkillsJars` into the gitignored `.kiro/skills/`); this file records only project-specific facts and exceptions.
- MCP server `sbta` (this build's `./sbta --mcp`, configured in `.mcp.json` and `.kiro/settings/mcp.json`, approved in `.claude/settings.json`): use it for all sbt work: `compile`, `test` (`full` for all), `run`, `check` after editing Scala 3 sources, `glob-search` / `inspect` / `symbol-location` for symbols, `sbt-task` for any other command. Its tools are deferred in Claude Code: load them with ToolSearch (search `sbta`). If it's unavailable, say so and use `./sbta <command>`. Logs: `project/target/sbta/mcp.log`.
- Maintenance routine: `.factory/MAINTENANCE.md` (weekly), following the `zen-of-projects` Skill.
- Validation: `./sbta "Test / compile; +scripted"` (scripted covers sbt 1 and sbt 2; use `./sbta` rather than sbt-mcp, because `+` cross-building reloads sbt and drops the MCP connection). It takes about 13 minutes.

## Supported sbt versions

This plugin supports sbt 1 and sbt 2 and keeps both: most builds that use it are still on sbt 1, and an sbt 2-only plugin can't be added to them.

`pluginCrossBuild / sbtVersion` in `build.sbt` sets the sbt each published artifact compiles against, which is the oldest sbt its users can run (zen-of-projects "sbt Plugin Projects"). These are floors, not dependencies: the maintenance routine leaves them alone.

- **sbt 1 (Scala 2.12): `1.13.1`.** The version it was compiled against when the floors were recorded (2026-11-04). It hasn't been tested against an older sbt 1, so it isn't lowered.
- **sbt 2 (Scala 3): `2.0.0`**, the first sbt 2 release, so every sbt 2 user can use it.

Raise a floor only when the plugin needs an API the floor lacks: say which in the PR, label it `needs-human` (it drops users on older sbt), and update this section.

