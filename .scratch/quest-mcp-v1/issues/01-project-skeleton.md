# 01: Project skeleton — mod loads in dev client

**What to build:** A buildable NeoForge mod project that a developer can launch with `runClient` and see Quest MCP loaded alongside FTB Quests and JEI. No MCP functionality yet; this makes every later ticket possible. See spec `.scratch/quest-mcp-v1/spec.md` (Platform & packaging).

**Blocked by:** None (can start immediately).

**Status:** done

- [x] ModDevGradle project for Minecraft 26.1.2 / NeoForge 26.1.2.x, Java 25 toolchain, based on the official MDK-26.1.2-ModDevGradle template
- [x] Mod ID `questmcp`, display name "Quest MCP", package `com.yshs.questmcp`, licence GPL-3.0 (licence file + mod metadata)
- [x] Mod is client-only and declares FTB Quests and JEI as required dependencies in its mod metadata
- [x] FTB Quests (26.1.2.9 line, with FTB Library + FTB Teams) and JEI (29.47.x for 26.1.2) resolve from their Maven repositories: compile against their APIs, present at dev runtime
- [x] `./gradlew build` succeeds and produces the mod jar; no third-party code is shaded into it
- [x] `runClient` starts the game; the mod list shows Quest MCP, FTB Quests and JEI; the mod logs a startup line
- [x] JUnit 5 is wired up and a trivial test runs under `./gradlew test` without needing a game
- [x] Build outputs and run directories are git-ignored

## Comments

- All criteria verified on the CNB machine: `./gradlew clean build` and `./gradlew test --rerun` pass (1 JUnit 5 test, no game). The jar contains only `QuestMcp.class`, `neoforge.mods.toml` and `LICENSE`; `jarJar` has no inputs. `runClient` under `xvfb-run` reached the title screen ("NeoForge 26.1.2.116 (8 mods)"). The Mods screen listed Quest MCP 0.1.0 (GPL-3.0-only), FTB Library 26.1.2.9, FTB Teams 26.1.2.4, FTB Quests 26.1.2.9 and JEI 29.47.0.109. The log showed `Quest MCP 0.1.0 loaded`. A new world loaded without errors: FTB Quests synced its quest file and JEI started.
- Client-only: `@Mod(dist = Dist.CLIENT)`, with the `ftbquests` and `jei` dependencies set to `type="required"` and `side="CLIENT"`. FML 11 has no `displayTest` key any more, so nothing else is needed.
- Only the `client` run is kept. `server` and `gameTestServer` don't apply to a client-only mod, and the mod has no gametests. `data` and the `src/generated/resources` source dir were removed because there is no datagen.
- JEI and its `net.mezzdev.config` dependency resolve from `modmaven.dev`, a mirror of maven.blamejared.com. From this machine, maven.blamejared.com timed out on every request. Each third-party repo is an `exclusiveContent` repo for its own groups.
