package com.playwithfriend;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import net.minecraft.server.MinecraftServer;

public class FriendBrain {
    private final HermesConfig cfg;
    private long lastCall = 0L;
    private String pendingPlan = null;
    /** Latest chat reply waiting for the server thread to send. */
    private volatile String pendingSay = null;
    private volatile FriendState pendingSayFor = null;

    public FriendBrain(HermesConfig cfg) {
        this.cfg = cfg;
    }

    /** Natural-chat entry: classify (harness) then talk, act, or switch mode. */
    public void answerChatAsync(final FriendState st, final String playerName, final String text) {
        if (!cfg.hasKey() || cfg.model == null || cfg.model.trim().isEmpty()) {
            st.doing = "Waiting for Hermes connect + model (title screen -> Hermes)";
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCall < 3000) {
            st.doing = "Thinking (rate-limited, one moment)...";
            return;
        }
        lastCall = now;
        st.doing = "Thinking...";
        new Thread(() -> {
            try {
                ensureFreshToken();
                String kind = classify(text);
                if ("MODE".equals(kind)) {
                    applyMode(st, text);
                } else if ("TASK".equals(kind)) {
                    String plan = callChat(cfg.model, planPrompt(st, playerName, text), 400);
                    plan = harnessParse(plan, text);
                    pendingPlan = plan;
                    st.goal = text.length() > 100 ? text.substring(0, 100) : text;
                    st.next = "Executing plan";
                    say(st, ackForTask(st, text));
                } else {
                    String reply = callChat(cfg.model, chatPrompt(st, playerName, text), 150);
                    say(st, reply);
                }
            } catch (Exception e) {
                String m = String.valueOf(e.getMessage());
                st.doing = "Hermes error: " + (m.length() > 120 ? m.substring(0, 120) : m);
            }
        }).start();
    }

    private void say(FriendState st, String text) {
        if (text == null) return;
        text = text.trim();
        if (text.isEmpty()) return;
        if (text.length() > 200) text = text.substring(0, 200);
        st.memory.did(st.name, text);
        pendingSayFor = st;
        pendingSay = text;
    }

    /** Server thread drains this (chat must send on the main thread). */
    public void drainSay(net.minecraft.server.MinecraftServer server) {
        if (pendingSay == null || pendingSayFor == null) return;
        String text = pendingSay;
        FriendState st = pendingSayFor;
        pendingSay = null;
        pendingSayFor = null;
        st.doing = "Chatting";
        server.getPlayerList().sendMessage(new net.minecraft.util.text.TextComponentString("<" + st.name + "> " + text));
    }

    public void requestPlanAsync(final FriendState st, final String userGoal) {
        if (!cfg.hasKey()) {
            st.doing = "Waiting for Hermes connect (title screen -> Hermes)";
            return;
        }
        if (cfg.model == null || cfg.model.trim().isEmpty()) {
            st.doing = "Pick a model first (title screen -> Hermes)";
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCall < 3000) return;
        lastCall = now;
        st.goal = userGoal;
        st.next = "Planning via " + cfg.model;
        new Thread(() -> {
            try {
                ensureFreshToken();
                String plan = callChat(cfg.model, userGoal, 400);
                plan = harnessParse(plan, userGoal);
                pendingPlan = plan;
            } catch (Exception e) {
                String m = String.valueOf(e.getMessage());
                st.doing = "Hermes error: " + (m.length() > 120 ? m.substring(0, 120) : m);
            }
        }).start();
    }

    public void tickAsync(FriendState st, MinecraftServer server) {
        if (pendingPlan != null) {
            st.executor.enqueuePlan(st, pendingPlan);
            st.doing = "Executing plan";
            pendingPlan = null;
        }
        drainSay(server);
    }

    private void ensureFreshToken() {
        if (!cfg.hasOAuth() || !cfg.tokenExpired()) return;
        if (cfg.refreshToken == null || cfg.refreshToken.isEmpty()) return;
        try {
            HermesPortal.Tokens t = HermesPortal.refresh(cfg.portalUrl, cfg.clientId, cfg.refreshToken);
            cfg.accessToken = t.accessToken;
            if (!t.refreshToken.isEmpty()) cfg.refreshToken = t.refreshToken;
            if (t.expiresInSec > 0) cfg.expiresAt = System.currentTimeMillis() + t.expiresInSec * 1000L;
            cfg.save();
        } catch (Exception ignored) {
        }
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "'").replace("\n", " ").replace("\r", " ");
    }

    private String callChat(String model, String userGoal, int maxTokens) throws Exception {
        String sys = "You control a Minecraft 1.12.2 FakePlayer companion. Return a short plan as lines like MINE <block> <n>, PLACE <block>, CRAFT <item>, FOLLOW, WAIT. No other text.";
        String text = rawChat(model, sys, userGoal, maxTokens);
        return text.length() > 2000 ? text.substring(0, 2000) : text;
    }

    /**
     * Cost-saving harness pass (mirrors the hermes-agent auxiliary-client idea):
     * the cheap DeepSeek model re-reads the planner's raw reply and returns ONLY
     * clean MINE/PLACE/CRAFT/FOLLOW/WAIT lines, so the selected (possibly paid)
     * model is used once for planning while parsing/normalizing rides on the
     * cheapest capable model.
     */
    private String harnessParse(String rawPlan, String userGoal) {
        if (!cfg.harnessEnabled) return rawPlan;
        String hm = cfg.harnessModel == null ? "" : cfg.harnessModel.trim();
        if (hm.isEmpty()) return rawPlan;
        try {
            ensureFreshToken();
            URL url = new URL(cfg.chatEndpoint());
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(20000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Authorization", "Bearer " + cfg.bearer());
            String sys = "Extract ONLY valid Minecraft companion plan lines (MINE <block> <n>, PLACE <block>, CRAFT <item>, FOLLOW, WAIT), one per line, max 20. Drop everything else. No explanations.";
            String body = "{\"model\":\"" + esc(hm) + "\",\"messages\":[{\"role\":\"system\",\"content\":\"" + esc(sys)
                + "\"},{\"role\":\"user\",\"content\":\"Goal: " + esc(userGoal) + " Planner reply: " + esc(rawPlan)
                + "\"}],\"max_tokens\":300}";
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(b.length);
            OutputStream os = c.getOutputStream();
            os.write(b);
            os.close();
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) return rawPlan;
            String clean = extractContent(readAll(c.getInputStream()));
            return clean.isEmpty() ? rawPlan : clean;
        } catch (Exception e) {
            return rawPlan;
        }
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Cheap harness classifies: CHAT (just talk), TASK (world action), MODE (follow/stay/come). */
    private String classify(String text) {
        if (!cfg.harnessEnabled || cfg.harnessModel == null || cfg.harnessModel.trim().isEmpty()) {
            return looksLikeTask(text) ? "TASK" : "CHAT";
        }
        try {
            ensureFreshToken();
            String sys = "Classify the player's message to their Minecraft companion. Reply with exactly one word: TASK if they want the companion to DO something in the world (get, build, mine, follow, come, stay, craft, kill, fetch, help with a job), MODE if they only change follow/stay/come behavior with no other job, CHAT otherwise (greetings, questions, jokes, status like what are you doing). One word only.";
            String r = rawChat(cfg.harnessModel, sys, text, 10);
            String u = r.trim().toUpperCase();
            if (u.startsWith("TASK")) return "TASK";
            if (u.startsWith("MODE")) return "MODE";
            if (u.startsWith("CHAT")) return "CHAT";
        } catch (Exception ignored) {
        }
        return looksLikeTask(text) ? "TASK" : "CHAT";
    }

    private static boolean looksLikeTask(String text) {
        String l = text.toLowerCase();
        return l.contains("get ") || l.contains("fetch") || l.contains("mine") || l.contains("build")
            || l.contains("craft") || l.contains("make ") || l.contains("collect") || l.contains("help me")
            || l.contains("follow") || l.contains("stay") || l.contains("come") || l.contains("kill")
            || l.contains("chop") || l.contains("dig");
    }

    private void applyMode(FriendState st, String text) {
        String l = text.toLowerCase();
        String mode = null;
        if (l.contains("follow")) mode = "Follow";
        else if (l.contains("stay")) mode = "Stay";
        if (mode != null) {
            st.mode = mode;
            say(st, mode.equals("Follow") ? "On my way, sticking with you." : "Got it, holding here.");
        } else if (l.contains("come")) {
            say(st, "Coming!");
            st.next = "COME";
        } else {
            say(st, "Sure thing.");
        }
    }

    private String ackForTask(FriendState st, String text) {
        String l = text.toLowerCase();
        if (l.contains("log") || l.contains("wood")) return "On it, grabbing some logs.";
        if (l.contains("house") || l.contains("build")) return "Nice, let's work on it. I'll start gathering.";
        return "Got it, on my way.";
    }

    private String chatPrompt(FriendState st, String playerName, String text) {
        return "You are " + st.name + ", a friendly Minecraft companion playing alongside " + playerName
            + ". Reply as yourself in 1-2 short sentences, casual gamer chat, no quotes around it. "
            + "Current job: " + (st.goal.isEmpty() ? "none" : st.goal) + ". Doing: " + st.doing + ". "
            + "Recent chat:\n" + st.memory.contextBlock(10)
            + playerName + ": " + text;
    }

    private String planPrompt(FriendState st, String playerName, String text) {
        return "Player " + playerName + " says to companion " + st.name + ": \"" + text + "\". "
            + "Recent chat:\n" + st.memory.contextBlock(10)
            + "Return a short plan as lines like MINE <block> <n>, PLACE <block>, CRAFT <item>, FOLLOW, WAIT. No other text.";
    }

    private String rawChat(String model, String sys, String user, int maxTokens) throws Exception {
        URL url = new URL(cfg.chatEndpoint());
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(20000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Authorization", "Bearer " + cfg.bearer());
        String body = "{\"model\":\"" + esc(model) + "\",\"messages\":[{\"role\":\"system\",\"content\":\"" + esc(sys)
            + "\"},{\"role\":\"user\",\"content\":\"" + esc(user) + "\"}],\"max_tokens\":" + maxTokens + "}";
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(b.length);
        OutputStream os = c.getOutputStream();
        os.write(b);
        os.close();
        int code = c.getResponseCode();
        InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
        String resp = readAll(in);
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        return extractContent(resp);
    }
    static String extractContent(String resp) {
        int i = resp.indexOf("\"content\"");
        if (i < 0) return resp.trim();
        int q = resp.indexOf('"', i + 9);
        if (q < 0) return resp.trim();
        StringBuilder sb = new StringBuilder();
        boolean escape = false;
        for (int k = q + 1; k < resp.length(); k++) {
            char ch = resp.charAt(k);
            if (escape) {
                if (ch == 'n') sb.append('\n');
                else if (ch == 't') sb.append(' ');
                else if (ch == 'r') sb.append(' ');
                else sb.append(ch);
                escape = false;
            } else if (ch == '\\') {
                escape = true;
            } else if (ch == '"') {
                break;
            } else {
                sb.append(ch);
            }
        }
        String t = sb.toString().trim();
        return t.isEmpty() ? resp.trim() : t;
    }
}
