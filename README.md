# PlayWithFriend (1.12.2 Forge)

AI player-friend + Hermes brain. Singleplayer, chunkloads like a player.

## ▶ Play it (no building needed)

**[⬇ Download the latest working mod jar](https://github.com/Sekai0NI0itamio/playwithfriend/releases/latest/download/playwithfriend-1.12.2.jar)**

Install: Minecraft 1.12.2 + Forge 14.23.5.2847 → drop the jar into `.minecraft/mods/` → launch → title screen Hermes button → in-world `/friend spawn Alex`.

> Built automatically by GitHub Actions on every successful `main` push. If the link 404s, no green build has published yet — check the [Actions tab](https://github.com/Sekai0NI0itamio/playwithfriend/actions) or use the [latest release page](https://github.com/Sekai0NI0itamio/playwithfriend/releases/latest).

## Setup (devs, CI builds only)
CI (`.github/workflows/build.yml`, Java 8 + Gradle 4.9) compiles every push. Per repo policy (`AGENTS.md`) never build locally.
Runtime: title screen -> Hermes button -> baseUrl + apiKey + model (saved to config/playwithfriend-hermes.json, never logged).
In world: `/friend spawn Alex`, `/friend do get 32 logs`, `/friend status`, `/friend follow|stay|come|despawn`.

## Arch (LLM plans, deterministic executes)
Hermes chat -> Goal/Plan lines (MINE/PLACE/CRAFT/FOLLOW/WAIT, max 20) -> ActionExecutor verifies block/inventory each tick, stuck>5s steps up + replans. No per-tick LLM movement.

## Safety
Allowlist only, confirm destructive, 3s rate-limit, key in local file only.

## Credits
- **Sekai0NI0itamio** — mod author (design, code, AI-companion architecture).
- **[MindCraft](https://github.com/kolbytn/mindcraft) by kolbytn et al. (MIT)** — inspiration for the AI-player concept and high-level planner/executor split; no MindCraft code is vendored here.
- **[MinecraftForge](https://github.com/MinecraftForge/MinecraftForge) 1.12.2 (LGPL-2.1)** — mod loader; `FakePlayer`/`FakePlayerFactory`, `ForgeChunkManager` chunk tickets, `EntityRegistry` entity system.
- **[MCP (Mod Coder Pack)](http://www.modcoderpack.com/) + ForgeGradle 2.3 (MIT)** — `stable_39` mappings and build toolchain.
- **[Gson](https://github.com/google/gson) by Google (Apache-2.0)** — Hermes config + API JSON.
- **Gradle 4.9 (Apache-2.0), Temurin JDK 8 (GPL-2.0 w/ Classpath Exception), GitHub Actions runners + `softprops/action-gh-release` (MIT)** — CI build and one-click release pipeline.
- **Hermes (user-configured model endpoint)** — external AI brain; users supply their own base URL, key, and model.
- **Mojang/Minecraft** — game assets belong to Mojang; this project ships code only, no game files or decompiled sources.
