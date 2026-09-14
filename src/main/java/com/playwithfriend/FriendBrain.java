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

    public FriendBrain(HermesConfig cfg) {
        this.cfg = cfg;
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
                String plan = callChat(cfg.model, userGoal, false);
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

    private String callChat(String model, String userGoal, boolean jsonMode) throws Exception {
        URL url = new URL(cfg.chatEndpoint());
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(30000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Authorization", "Bearer " + cfg.bearer());
        String sys = "You control a Minecraft 1.12.2 FakePlayer companion. Return a short plan as lines like MINE <block> <n>, PLACE <block>, CRAFT <item>, FOLLOW, WAIT. No other text.";
        String body = "{\"model\":\"" + esc(model) + "\",\"messages\":[{\"role\":\"system\",\"content\":\"" + esc(sys)
            + "\"},{\"role\":\"user\",\"content\":\"" + esc(userGoal) + "\"}],\"max_tokens\":300}";
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(b.length);
        OutputStream os = c.getOutputStream();
        os.write(b);
        os.close();
        int code = c.getResponseCode();
        InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
        String resp = readAll(in);
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + (resp.length() > 200 ? resp.substring(0, 200) : resp));
        String text = extractContent(resp);
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

    /** Minimal chat-completions content extractor (no extra deps). */
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
