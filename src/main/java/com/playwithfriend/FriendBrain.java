package com.playwithfriend;

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
            st.doing = "Waiting for Hermes login (title screen -> Hermes)";
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCall < 3000) return;
        lastCall = now;
        st.goal = userGoal;
        st.next = "Planning via " + cfg.model;
        new Thread(() -> {
            try {
                String plan = callHermes(st, userGoal);
                pendingPlan = plan;
            } catch (Exception e) {
                st.doing = "Hermes error: " + e.getMessage();
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

    private String callHermes(FriendState st, String userGoal) throws Exception {
        URL url = new URL(cfg.baseUrl + "/chat/completions");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Authorization", "Bearer " + cfg.apiKey);
        String sys = "You control a Minecraft 1.12.2 FakePlayer companion. Return a short plan as lines like MINE <block> <n>, PLACE <block>, CRAFT <item>, FOLLOW, WAIT. No other text.";
        String body = "{\"model\":\"" + cfg.model + "\",\"messages\":[{\"role\":\"system\",\"content\":\"" + sys + "\"},{\"role\":\"user\",\"content\":\"" + userGoal.replace("\"", "'") + "\"}],\"max_tokens\":300}";
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(b.length);
        OutputStream os = c.getOutputStream();
        os.write(b);
        os.close();
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        byte[] buf = new byte[8192];
        int n = c.getInputStream().read(buf);
        String resp = new String(buf, 0, Math.max(0, n), StandardCharsets.UTF_8);
        return resp.length() > 2000 ? resp.substring(0, 2000) : resp;
    }
}
