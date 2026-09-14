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

    /** Natural-chat entry: everything becomes an agent job. Pure chatter
     * ("hi bob") just runs a 1-2 step session (say, done); anything with a
     * job attached runs the full observe->think->act loop. */
    public void answerChatAsync(final FriendState st, final String playerName, final String text) {
        if (!cfg.hasKey() || cfg.model == null || cfg.model.trim().isEmpty()) {
            st.doing = "Waiting for Hermes connect + model (title screen -> Hermes)";
            return;
        }
        if (st.agent.hasWork()) {
            // New message interrupts the old job (cancel is first-class).
            st.agent.cancel("interrupted by player");
        }
        st.memory.said(st.ownerId, playerName, text);
        lastCall = System.currentTimeMillis();
        st.doing = "Thinking...";
        MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        st.agent.start(st, server, playerName + " says: " + text);
        FriendLogger.info(st, "JOB from " + playerName + ": " + text);
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
        // /friend do feeds the same agent loop (chat path without a speaker).
        answerChatAsync(st, "Player", userGoal);
    }

    public void tickAsync(FriendState st, MinecraftServer server) {
        st.agent.drain(st, server);
        pumpMovement(st, server);
        pumpDigging(st, server);
    }

    /** Walk goto targets to arrival (re-path if stalled, fail loudly if lost). */
    private void pumpMovement(FriendState st, MinecraftServer server) {
        if (st.gotoTarget == null || st.visible == null) return;
        double[] t = st.gotoTarget;
        double d = st.visible.getDistance(t[0], t[1], t[2]);
        if (d < 1.5D) {
            st.gotoTarget = null;
            return;
        }
        if (st.visible.getNavigator().noPath()) {
            boolean ok = st.visible.getNavigator().tryMoveToXYZ(t[0], t[1], t[2], 1.0D);
            if (!ok) {
                st.gotoTarget = null;
            }
        }
    }

    /** Break one block per few ticks toward digTarget (verified, drops to proxy). */
    private int digCooldown = 0;

    private void pumpDigging(FriendState st, MinecraftServer server) {
        if (st.digTarget == null || st.visible == null) return;
        if (digCooldown-- > 0) return;
        digCooldown = 4;
        net.minecraft.util.math.BlockPos target = st.digTarget;
        double d = st.visible.getDistance(target.getX(), target.getY(), target.getZ());
        if (d > 6.0D) {
            if (st.visible.getNavigator().noPath()) {
                st.visible.getNavigator().tryMoveToXYZ(target.getX(), target.getY(), target.getZ(), 1.0D);
            }
            st.doing = "Walking to dig site (" + ((int) d) + "m)";
            return;
        }
        if (!st.visible.world.isBlockLoaded(target)) {
            st.doing = "Waiting for chunk at dig site";
            return;
        }
        net.minecraft.block.state.IBlockState s = st.visible.world.getBlockState(target);
        if (s.getBlock() == net.minecraft.init.Blocks.AIR) {
            st.digTarget = null;
            return;
        }
        if (st.digBudget-- <= 0) {
            st.digTarget = null;
            return;
        }
        st.visible.world.destroyBlock(target, true);
        if (st.proxy != null) st.proxy.inventory.addItemStackToInventory(new net.minecraft.item.ItemStack(net.minecraft.init.Items.STICK, 0));
        st.doing = "Digging (" + st.digBudget + " left)";
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

    /**
     * One think step for the agent loop (off-thread). Returns raw model text;
     * the loop parses CALL lines. Uses the harness model for cheap steps when
     * the job looks trivial, else the user's selected model.
     */
    public String think(String job, String snapshot, String turnHistory, boolean saidFirst) throws Exception {
        ensureFreshToken();
        String model = pickThinkModel(job, turnHistory);
        String sys = "You are Bob-style Minecraft companion " + "(remote-controlling a game body; you are NOT physically in the game, say so if asked). "
            + "Talk in very short sentences (say tool max 80 chars). Be yourself, casual, no roleplay fluff.\n"
            + "TOOLS (reply ONLY with CALL lines, up to 3, one per line):\n"
            + AgentTool.spec()
            + "RULES: First reply MUST contain CALL say <short msg> AND one action CALL. Talk tools (say) and read tools (status, get_block, scan) may share a step; act tools (goto, dig_to, place_at, craft, give, follow, stay, come, stop, done) are ONE per step. "
            + "done ends the job. stop cancels everything. Keep going until done; report failures honestly via say.";
        String user = "JOB: " + job + "\nWORLD: " + snapshot + "\nHISTORY:\n" + turnHistory
            + (saidFirst ? "" : "\n(This is step 1: you MUST include CALL say + one action CALL.)");
        String reply = rawChat(model, sys, user, 300);
        FriendLogger.think(lastThinkState, reply);
        return reply;
    }

    private FriendState lastThinkState;

    public void bindThinkState(FriendState st) {
        lastThinkState = st;
    }

    private String pickThinkModel(String job, String turnHistory) {
        // Harness: real work uses the pick; trivial chatter rides the cheap model.
        if (!cfg.harnessEnabled || cfg.harnessModel == null || cfg.harnessModel.trim().isEmpty()) return cfg.model;
        if (cfg.harnessModel.equals(cfg.model)) return cfg.model;
        String l = (job + " " + turnHistory).toLowerCase();
        boolean trivial = (l.contains("hello") || l.contains(" hi ") || l.startsWith("hi ") || l.contains("thanks")
            || l.contains("what are you doing") || l.contains("status")) && !looksLikeTask(job);
        return trivial ? cfg.harnessModel : cfg.model;
    }

    /** Legacy plan-line path kept while the executor still exists (deprecated). */
    private static boolean looksLikeTask(String text) {
        String l = text.toLowerCase();
        return l.contains("get ") || l.contains("fetch") || l.contains("mine") || l.contains("build")
            || l.contains("craft") || l.contains("make ") || l.contains("collect") || l.contains("help me")
            || l.contains("follow") || l.contains("stay") || l.contains("come") || l.contains("kill")
            || l.contains("chop") || l.contains("dig");
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
