# PlayWithFriend (1.12.2 Forge)

AI player-friend + Hermes brain. Singleplayer, chunkloads like a player.

## ▶ Play it (no building needed)

**[⬇ Download the latest working mod jar](https://github.com/Sekai0NI0itamio/playwithfriend/releases/latest/download/playwithfriend-1.12.2.jar)**

Install: Minecraft 1.12.2 + Forge 14.23.5.2847 → drop the jar into `.minecraft/mods/` → launch → title screen Hermes button → in-world `/friend spawn Alex`.

> Built automatically by GitHub Actions on every successful `main` push. If the link 404s, no green build has published yet — check the [Actions tab](https://github.com/Sekai0NI0itamio/playwithfriend/actions) or use the [latest release page](https://github.com/Sekai0NI0itamio/playwithfriend/releases/latest).

## Test it in game (5 min)
1. Install: vanilla launcher, Minecraft 1.12.2 + Forge 14.23.5.2847, drop the jar above into `.minecraft/mods/`, launch.
2. Title screen -> Hermes button (top-left, shows connect state) -> Connect Hermes account -> approve the shown code in the auto-opened Portal page (URL also drawn in-game + "Open login page" reopens it) -> wait for "Connected! Loading models...".
3. Model list: mouse-wheel / scrollbar / drag all scroll; click a row -> it shows `[x]`, "Save model" enables; Filter button cycles All -> Free only -> Paid only; double-click also saves. Saved model is prefixed `>` and shown on the title button + status line.
4. Singleplayer world -> `/friend spawn Bob` -> just talk in chat: `hello bob, lets work on this house, can you help me get some logs?` -> Bob replies (`<Bob> ...`) and starts the job. More talk: `bob, follow me` / `hey bob, what are you doing?` / `thanks bob, stay here`. Commands still work: `/friend status`, `/friend do get 32 logs`, `/friend follow|stay|come|despawn`.
5. If anything looks off, screenshot the screen + paste `.minecraft/logs/latest.log` lines mentioning `playwithfriend`.

## Setup (devs, CI builds only)
CI (`.github/workflows/build.yml`, Java 8 + Gradle 4.9) compiles every push. Per repo policy (`AGENTS.md`) never build locally.
Runtime: title screen -> Hermes button -> Connect Hermes account (code + browser approve, like `hermes setup --portal`) -> pick a model (All/Free/Paid filter, prices + context + modalities shown) -> Save. Token stays in config/playwithfriend-hermes.json, never logged.
In world: `/friend spawn Alex`, `/friend do get 32 logs`, `/friend status`, `/friend follow|stay|come|despawn`.

## Arch (agentic tool loop, remote-controller identity)
Every message starts an agent session: observe (world snapshot) -> think (your model; trivial chatter rides the cheap DeepSeek harness) -> CALL tools -> game-thread executes -> result feeds back, max 20 steps / 3 min / 3 fails / 3 repeats, then it pauses and says so. Tools: say (chat, FIRST call of every job is say + one action), status, get_block, scan, goto, dig_to, place_at, craft, give, follow, stay, come, stop, done. Only say reaches in-game chat; every reply + tool + result is logged to config/playwithfriend-agent.log. The friend admits it remote-controls a body; cancel (new message / stop) is first-class. Auth + catalog mirror the official [hermes-agent CLI](https://github.com/NousResearch/hermes-agent) (Nous Portal OAuth device-code, free-tier gating, Portal + OpenRouter model data).

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
