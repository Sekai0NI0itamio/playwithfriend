# Criteria: PlayWithFriend v1 (1.12.2 Forge, Hermes brain, FakePlayer friend)

- [ ] `gradlew setupDecompWorkspace build` scaffolds without hand-editing MDK (Forge 14.23.5, Gradle 4.9, mapping stable_39, Java 8)
- [ ] `/friend spawn|despawn|follow|stay|come|status` works in singleplayer integrated server, 1+ friends
- [ ] Friend is server `FakePlayer` (chunkloads like real player via ForgeChunkManager ticket, bounded radius, shown in UI) and renders as player on client with no custom skin crash
- [ ] Agentic loop: every message starts a session (observe->think->CALL->execute->result), first reply is say + one action, short remote-controller voice, budgets pause + announce, cancel works mid-job, only say reaches chat, agent log has every reply/tool/result
- [ ] Spawn never crashes (per-friend ticket + loading callback); friend renders as player skin (no black box), moves via AI tasks with step/jump/head-turn, no spin or wall-clip
- [ ] Stuck detection -> recovery -> replan; never stares at wall >5s without chat/status update
- [ ] Hermes connect from title screen uses Nous Portal OAuth device-code (button -> code + auto-open browser -> approve -> token stored locally, never logged, auto-refresh); no API-key typing
- [ ] Model list scrolls (wheel + scrollbar + drag), click selects with [x] marker, Save enables and persists, saved model shows > marker + on title button; Connect/Open/Filter/Save/Back/Disconnect all visibly enable/disable with state (never stuck grey); approval URL drawn in-game as fallback
- [ ] Cost harness: every plan goes planner model -> cheap DeepSeek pass that returns only clean plan lines; harness failure falls back to raw plan, never blocks; free-tier users see only free models selectable
- [ ] Natural chat: "hello bob, ..." / "bob, ..." / "..., bob" routes to Bob only (others ignore), re-broadcast keeps vanilla chat intact; harness classifies CHAT (replies in voice with memory, <=200 chars, server-thread send) vs TASK (plans via planner + harness) vs MODE (follow/stay/come); "what are you doing?" answers from real Goal/Doing state
- [ ] Safety (llm-security): tool allowlist, human confirm for destructive/high-impact (lava, TNT, throw valuables), rate-limited Hermes calls, outputs sanitized before commands
- [ ] Failure cases do not corrupt world: tool break aborts + reverts to follow; companion death drops + recoverable; GUI close / teleport / unload resumes or reports, never dupes items
