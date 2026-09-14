# Criteria: PlayWithFriend v1 (1.12.2 Forge, Hermes brain, FakePlayer friend)

- [ ] `gradlew setupDecompWorkspace build` scaffolds without hand-editing MDK (Forge 14.23.5, Gradle 4.9, mapping stable_39, Java 8)
- [ ] `/friend spawn|despawn|follow|stay|come|status` works in singleplayer integrated server, 1+ friends
- [ ] Friend is server `FakePlayer` (chunkloads like real player via ForgeChunkManager ticket, bounded radius, shown in UI) and renders as player on client with no custom skin crash
- [ ] Planning != execution: Hermes LLM returns Goal/Plan JSON, deterministic `ActionExecutor` does mine/place/craft/smelt/equip/eat/open-container/attack step-by-step with verify-after-every-action (block changed, item in inventory)
- [ ] Stuck detection -> recovery -> replan; never stares at wall >5s without chat/status update
- [ ] Hermes connect from title screen uses Nous Portal OAuth device-code (button -> code + auto-open browser -> approve -> token stored locally, never logged, auto-refresh); no API-key typing
- [ ] After connect, model list loads (Portal recs + inference /models + OpenRouter prices) with All/Free/Paid filter; each row shows FREE/PAID, price in/out per 1M, context length, input/output modalities; selecting + Save persists
- [ ] Cost harness: every plan goes planner model -> cheap DeepSeek pass that returns only clean plan lines; harness failure falls back to raw plan, never blocks; free-tier users see only free models selectable
- [ ] Explainability: `/friend status` + overhead/chat line always shows Goal / Doing / Next; `What are you doing?` answers from real state, not hallucinated
- [ ] Safety (llm-security): tool allowlist, human confirm for destructive/high-impact (lava, TNT, throw valuables), rate-limited Hermes calls, outputs sanitized before commands
- [ ] Failure cases do not corrupt world: tool break aborts + reverts to follow; companion death drops + recoverable; GUI close / teleport / unload resumes or reports, never dupes items
