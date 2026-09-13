# PlayWithFriend (1.12.2 Forge)

FakePlayer AI friend + Hermes brain. Singleplayer, chunkloads like a player.

## Setup (Java 8 only)
1. Install JDK 8 64-bit.
2. Copy `gradlew`, `gradlew.bat`, `gradle/` from official Forge 1.12.2 MDK (14.23.5.2855) into this folder (licensing: don't redistribute those).
3. `gradlew setupDecompWorkspace genIntellijRuns` (or genEclipseRuns), then `gradlew build`.
4. Title screen -> Hermes button -> baseUrl + apiKey + model (saved to config/playwithfriend-hermes.json, never logged).
5. In world: `/friend spawn Alex`, `/friend do get 32 logs`, `/friend status`, `/friend follow|stay|come|despawn`.

## Arch (LLM plans, deterministic executes)
Hermes chat -> Goal/Plan lines (MINE/PLACE/CRAFT/FOLLOW/WAIT, max 20) -> ActionExecutor verifies block/inventory each tick, stuck>5s steps up + replans. No per-tick LLM movement.

## Safety
Allowlist only, confirm destructive, 3s rate-limit, key in local file only.
