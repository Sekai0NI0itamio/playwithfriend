package com.playwithfriend;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;

/**
 * One agentic session per friend. The loop runs OFF the server thread:
 * observe (snapshot) -> think (LLM tool call) -> validate -> queue for the
 * server thread -> result -> repeat. Budgets (steps / wall time / consecutive
 * failures / repeats) pause the loop instead of spinning forever. The game
 * thread only executes validated tools via drain() and pumps movement/digging.
 */
public class AgentSession {
    public static final int MAX_STEPS = 20;
    public static final long MAX_MS = 3 * 60 * 1000L;
    public static final int MAX_FAILS = 3;
    public static final int MAX_REPEATS = 3;

    private final FriendBrain brain;
    private volatile boolean running;
    private volatile boolean cancelled;
    private volatile String cancelWhy = "";
    private volatile int steps;
    private volatile long startedAt;
    private volatile int fails;
    private volatile String lastTool = "";
    private volatile int repeats;
    private volatile String job = "";
    private volatile boolean saidFirst;
    private final List<String> history = new ArrayList<String>();
    private final java.util.Queue<PendingCall> queue = new java.util.ArrayDeque<PendingCall>();
    private volatile String lastResult = "";

    private static class PendingCall {
        String tool;
        String args;
    }

    public AgentSession(FriendBrain brain) {
        this.brain = brain;
    }

    public synchronized boolean hasWork() {
        return running || !queue.isEmpty();
    }

    public synchronized void cancel(String why) {
        cancelled = true;
        cancelWhy = why == null ? "" : why;
        queue.clear();
    }

    public synchronized boolean isCancelled() {
        return cancelled;
    }

    public void start(final FriendState st, final MinecraftServer server, final String jobText) {
        cancel("new job");
        running = true;
        cancelled = false;
        steps = 0;
        fails = 0;
        repeats = 0;
        job = jobText;
        saidFirst = false;
        startedAt = System.currentTimeMillis();
        synchronized (history) {
            history.clear();
        }
        st.goal = jobText.length() > 100 ? jobText.substring(0, 100) : jobText;
        new Thread(() -> loop(st, server)).start();
    }

    private void loop(FriendState st, MinecraftServer server) {
        while (!cancelled) {
            if (steps >= MAX_STEPS || System.currentTimeMillis() - startedAt > MAX_MS
                || fails >= MAX_FAILS || repeats >= MAX_REPEATS) {
                pause(st, server, "budget");
                return;
            }
            String snap = snapshot(st, server);
            String reply;
            try {
                reply = brain.think(st, job, snap, drainHistory(), saidFirst);
            } catch (Exception e) {
                fails++;
                lastResult = "ERR LLM: " + e.getMessage();
                sleep(1000);
                continue;
            }
            List<ToolCall> calls = ToolCall.parse(reply);
            if (calls.isEmpty()) {
                fails++;
                pushHist("model", reply);
                lastResult = "ERR no tool call parsed; reply with CALL <tool> <args>";
                continue;
            }
            // say-first protocol: first response must include say + one action.
            if (!saidFirst) {
                boolean hasSay = false;
                boolean hasAction = false;
                for (ToolCall c : calls) {
                    if ("say".equals(c.tool)) hasSay = true;
                    else hasAction = true;
                }
                if (!hasSay || !hasAction) {
                    pushHist("model", reply);
                    lastResult = "ERR protocol: first reply must be CALL say <short msg> AND one action CALL (e.g. CALL goto ...). Try again.";
                    continue;
                }
                saidFirst = true;
            }
            boolean acted = false;
            for (ToolCall c : calls) {
                AgentTool tool = find(c.tool);
                if (tool == null) {
                    lastResult = "ERR unknown tool <" + c.tool + ">";
                    fails++;
                    continue;
                }
                if (!tool.parallelSafe() && acted) {
                    lastResult = "ERR serialize: only one act-tool per step; split across steps";
                    fails++;
                    continue;
                }
                if (tool.parallelSafe()) {
                    // talk/read run now-ish via queue too (server thread owns chat/world reads).
                    enqueue(c.tool, c.args);
                } else {
                    enqueue(c.tool, c.args);
                    acted = true;
                }
                if (c.tool.equals(lastTool)) repeats++;
                else repeats = 0;
                lastTool = c.tool;
                steps++;
                pushHist("model", "CALL " + c.tool + " " + c.args);
            }
            // Wait for the server thread to execute and report back.
            // resultReady MUST be false here: it starts true, so without this
            // the loop would consume a phantom result before drain() runs.
            synchronized (queue) {
                resultReady = false;
            }
            String res = waitResult();
            if (res == null) return;
            lastResult = res;
            pushHist("game", res);
            if (res.startsWith("OK")) fails = 0;
            else fails++;
            if (res.startsWith("OK job closed") || res.startsWith("OK stopped")) {
                running = false;
                return;
            }
        }
        running = false;
    }

    private void pause(FriendState st, MinecraftServer server, String why) {
        running = false;
        String note = "I got stuck, so I paused (" + why + ", step " + steps + ").";
        PendingCall p = new PendingCall();
        p.tool = "say";
        p.args = note;
        synchronized (queue) {
            queue.add(p);
        }
        st.doing = "Paused (" + why + ")";
    }

    private String snapshot(FriendState st, MinecraftServer server) {
        StringBuilder sb = new StringBuilder();
        try {
            net.minecraft.util.math.BlockPos p = st.visible.getPosition();
            sb.append("pos=").append(p.getX()).append(',').append(p.getY()).append(',').append(p.getZ());
            sb.append(" hp=").append((int) st.visible.getHealth());
            sb.append(" mode=").append(st.mode);
            sb.append(" doing=").append(st.doing);
            sb.append(" time=").append(st.visible.world.getWorldTime() % 24000);
            sb.append(st.visible.world.isDaytime() ? "(day)" : "(night)");
            sb.append(" weather=").append(st.visible.world.isRaining() ? "rain" : "clear");
            net.minecraft.entity.player.EntityPlayerMP o = st.ownerId == null ? null
                : server.getPlayerList().getPlayerByUUID(st.ownerId);
            if (o != null) {
                sb.append(" owner=").append((int) o.posX).append(',').append((int) o.posY).append(',').append((int) o.posZ);
                sb.append(" ownerDist=").append((int) st.visible.getDistance(o));
                sb.append(" ownerHp=").append((int) o.getHealth());
                sb.append(" ownerHolding=").append(o.getHeldItemMainhand().getDisplayName());
            }
            // Nearby threats/friends (cheap 12-block scan).
            int mobs = 0;
            String mobNames = "";
            for (Object e : st.visible.world.loadedEntityList) {
                if (!(e instanceof net.minecraft.entity.EntityLiving)) continue;
                net.minecraft.entity.EntityLiving el = (net.minecraft.entity.EntityLiving) e;
                if (el == st.visible || el.isDead) continue;
                if (el.getDistance(st.visible) > 12) continue;
                mobs++;
                if (mobNames.length() < 80) {
                    if (!mobNames.isEmpty()) mobNames += ",";
                    mobNames += el.getName();
                }
            }
            sb.append(" nearbyMobs=").append(mobs).append("[").append(mobNames).append("]");
            StringBuilder inv = new StringBuilder();
            if (st.proxy != null) {
                for (int i = 0; i < st.proxy.inventory.getSizeInventory(); i++) {
                    net.minecraft.item.ItemStack s = st.proxy.inventory.getStackInSlot(i);
                    if (!s.isEmpty()) {
                        if (inv.length() > 0) inv.append(",");
                        inv.append(s.getCount()).append("x").append(s.getDisplayName());
                        if (inv.length() > 160) break;
                    }
                }
            }
            sb.append(" inv=[").append(inv.length() == 0 ? "empty" : inv.toString()).append("]");
            sb.append(" lastResult=").append(lastResult.length() > 300 ? lastResult.substring(0, 300) : lastResult);
        } catch (Exception e) {
            sb.append("snapshot-err ").append(e.getMessage());
        }
        return sb.toString();
    }

    private String drainHistory() {
        synchronized (history) {
            StringBuilder sb = new StringBuilder();
            int from = Math.max(0, history.size() - 12);
            for (int i = from; i < history.size(); i++) sb.append(history.get(i)).append('\n');
            return sb.toString();
        }
    }

    private void pushHist(String who, String text) {
        synchronized (history) {
            history.add(who + ": " + text);
            while (history.size() > 40) history.remove(0);
        }
    }

    private void enqueue(String tool, String args) {
        PendingCall p = new PendingCall();
        p.tool = tool;
        p.args = args;
        synchronized (queue) {
            queue.add(p);
        }
    }

    private String waitResult() {
        long deadline = System.currentTimeMillis() + 60000L;
        while (!cancelled && System.currentTimeMillis() < deadline) {
            if (lastResultDrained()) return lastResult;
            sleep(100);
        }
        return cancelled ? null : "ERR TIMEOUT no result in 60s";
    }

    private volatile boolean resultReady = true;

    private boolean lastResultDrained() {
        return resultReady;
    }

    /** Server thread: run queued tools, feed results back. Returns after one act-tool or all talk/read. */
    public void drain(FriendState st, MinecraftServer server) {
        while (true) {
            PendingCall p;
            synchronized (queue) {
                p = queue.peek();
            }
            if (p == null) return;
            AgentTool tool = find(p.tool);
            if (tool == null) {
                synchronized (queue) {
                    queue.poll();
                }
                lastResult = "ERR unknown tool <" + p.tool + ">";
                resultReady = true;
                continue;
            }
            resultReady = false;
            String r;
            try {
                r = tool.run(st, server, p.args);
            } catch (Exception e) {
                r = "ERR " + p.tool + " crashed: " + e.getMessage();
            }
            synchronized (queue) {
                queue.poll();
            }
            lastResult = r;
            resultReady = true;
            FriendLogger.tool(st, p.tool, p.args, r);
            if (!tool.parallelSafe()) return;
        }
    }

    private static AgentTool find(String name) {
        for (AgentTool t : AgentTool.all()) {
            if (t.name.equalsIgnoreCase(name)) return t;
        }
        return null;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    /** Minimal CALL parser: lines "CALL <tool> <args...>" (args may be empty). */
    public static class ToolCall {
        public String tool;
        public String args = "";

        public static List<ToolCall> parse(String reply) {
            List<ToolCall> out = new ArrayList<ToolCall>();
            if (reply == null) return out;
            for (String line : reply.split("[\\r\\n]+")) {
                String t = line.trim();
                if (!t.regionMatches(true, 0, "CALL ", 0, 5)) continue;
                String rest = t.substring(5).trim();
                if (rest.isEmpty()) continue;
                ToolCall c = new ToolCall();
                int sp = rest.indexOf(' ');
                if (sp < 0) {
                    c.tool = rest.toLowerCase();
                } else {
                    c.tool = rest.substring(0, sp).toLowerCase();
                    c.args = rest.substring(sp + 1).trim();
                }
                out.add(c);
                if (out.size() >= 3) break;
            }
            return out;
        }
    }
}
