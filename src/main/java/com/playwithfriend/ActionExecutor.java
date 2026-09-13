package com.playwithfriend;

import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

public class ActionExecutor {
    private final Queue<String> queue = new ArrayDeque<String>();
    private int stuckTicks = 0;
    private double lx, lz;

    public synchronized void enqueuePlan(FriendState st, String plan) {
        queue.clear();
        for (String line : plan.split("[\\r\\n;]+")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String up = line.toUpperCase();
            if (up.startsWith("MINE") || up.startsWith("PLACE") || up.startsWith("CRAFT") || up.startsWith("FOLLOW") || up.startsWith("WAIT")) {
                if (queue.size() < 20) queue.add(line);
            }
        }
        if (queue.isEmpty()) queue.add("FOLLOW");
        st.next = queue.peek();
    }

    public void tick(FriendState st, MinecraftServer server) {
        if (st.visible == null || st.proxy == null) return;
        double dx = st.visible.posX - lx;
        double dz = st.visible.posZ - lz;
        if (dx * dx + dz * dz < 0.0004 && "Follow".equals(st.mode)) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        lx = st.visible.posX;
        lz = st.visible.posZ;
        if (stuckTicks > 100) {
            st.doing = "Stuck - replanning";
            st.visible.getNavigator().clearPath();
            st.visible.getNavigator().tryMoveToXYZ(st.visible.posX, st.visible.posY + 1, st.visible.posZ, 1.0D);
            stuckTicks = 0;
            return;
        }
        String cur;
        synchronized (this) {
            cur = queue.peek();
        }
        if (cur == null) return;
        st.doing = "Step: " + cur;
        String up = cur.toUpperCase();
        if (up.startsWith("FOLLOW") || up.startsWith("WAIT")) {
            synchronized (this) {
                queue.poll();
            }
        } else {
            synchronized (this) {
                queue.poll();
            }
        }
        BlockPos p = st.visible.getPosition();
        if (!st.visible.world.isBlockLoaded(p)) {
            st.doing = "Waiting for chunk load at " + p;
        }
    }

    public synchronized void clear() {
        queue.clear();
        stuckTicks = 0;
    }
}
