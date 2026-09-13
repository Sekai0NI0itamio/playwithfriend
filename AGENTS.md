# AGENTS.md — PlayWithFriend

## Iron rule: NEVER build the mod locally

- Do NOT run `gradlew`, `gradle build`, `setupDecompWorkspace`, or any compile deobfuscation task on this machine. No exceptions.
- All compilation happens in GitHub Actions (`.github/workflows/build.yml`, Java 8 + Gradle 4.9). Push and read the CI result instead.
- Local `javac` syntax peeks are also off-limits — they produce misleading errors without the Forge MDK and violate this rule's spirit.
- What you MAY do locally: read files, edit sources, write docs, `git status`/`git diff`, `gh` read-only commands.

## Project facts

- Minecraft 1.12.2, Forge 14.23.5.2855, ForgeGradle 2.3, mappings stable_39, Java 8 (CI-only).
- Remote: https://github.com/Sekai0NI0itamio/playwithfriend (empty at scaffold time).
- Hermes brain config lives in `config/playwithfriend-hermes.json` at runtime; never commit real keys.
