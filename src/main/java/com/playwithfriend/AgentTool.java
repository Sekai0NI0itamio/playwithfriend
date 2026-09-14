package com.playwithfriend;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

/**
 * One tool call the agent can run. run() executes ONLY on the server thread
 * and returns a structured result string the agent feeds back to the model.
 * kind: "talk" (parallel-safe) | "read" (parallel-safe) | "act" (exclusive).
 */
public abstract class AgentTool {
    public final String name;
    public final String kind;
    public final String usage;

    protected AgentTool(String name, String kind, String usage) {
        this.name = name;
        this.kind = kind;
        this.usage = usage;
    }

    public boolean parallelSafe() {
        return "talk".equals(kind) || "read".equals(kind);
    }

    public abstract String run(FriendState st, MinecraftServer server, String args);

    protected static EntityPlayerMP owner(FriendState st, MinecraftServer server) {
        if (st.ownerId == null) return null;
        return server.getPlayerList().getPlayerByUUID(st.ownerId);
    }

    protected static BlockPos friendPos(FriendState st) {
        return st.visible.getPosition();
    }

    protected static String blockName(IBlockState s) {
        try {
            return s.getBlock().getRegistryName().toString();
        } catch (Exception e) {
            return "?";
        }
    }

    /** All tools the agent may call. Order here = order shown to the model. */
    public static List<AgentTool> all() {
        List<AgentTool> t = new ArrayList<AgentTool>();
        t.add(new SayTool());
        t.add(new StatusTool());
        t.add(new GetBlockTool());
        t.add(new ScanTool());
        t.add(new GotoTool());
        t.add(new DigToTool());
        t.add(new PlaceAtTool());
        t.add(new CraftTool());
        t.add(new GiveTool());
        t.add(new FollowTool());
        t.add(new StayTool());
        t.add(new ComeTool());
        t.add(new StopTool());
        t.add(new DoneTool());
        t.add(new RememberTool());
        return t;
    }

    public static String spec() {
        StringBuilder sb = new StringBuilder();
        for (AgentTool t : all()) {
            sb.append("- ").append(t.name).append(" (").append(t.kind).append("): ").append(t.usage).append('\n');
        }
        return sb.toString();
    }

    // ---- talk ----

    static class SayTool extends AgentTool {
        SayTool() {
            super("say", "talk", "say <short message, max 80 chars>: chat one short sentence to the player. FIRST call of every job MUST be say + one action tool together.");
        }

        @Override
        public String run(FriendState st, MinecraftServer server, String args) {
            String m = args == null ? "" : args.trim();
            if (m.length() > 80) m = m.substring(0, 80);
            if (m.isEmpty()) return "ERR say needs a message";
            st.memory.did(st.name, m);
            server.getPlayerList().sendMessage(new TextComponentString("<" + st.name + "> " + m));
            return "OK said: " + m;
        }
    }

    // ---- read ----

    static class StatusTool extends AgentTool {
        StatusTool() {
            super("status", "read", "status: report position, health, hunger proxy, inventory summary, current goal.");
        }

        @Override
        public String run(FriendState st, MinecraftServer server, String args) {
            BlockPos p = friendPos(st);
            StringBuilder inv = new StringBuilder();
            if (st.proxy != null) {
                for (int i = 0; i < st.proxy.inventory.getSizeInventory(); i++) {
                    ItemStack s = st.proxy.inventory.getStackInSlot(i);
                    if (!s.isEmpty()) {
                        if (inv.length() > 0) inv.append(", ");
                        inv.append(s.getCount()).append("x ").append(s.getDisplayName());
                        if (inv.length() > 220) break;
                    }
                }
            }
            return "OK pos=" + p.getX() + "," + p.getY() + "," + p.getZ()
                + " hp=" + ((int) st.visible.getHealth())
                + " goal=" + st.goal
                + " doing=" + st.doing
                + " inv=[" + (inv.length() == 0 ? "empty" : inv.toString()) + "]";
        }
    }

    static class GetBlockTool extends AgentTool {
        GetBlockTool() {
            super("get_block", "read", "get_block <x> <y> <z> OR get_block <name>: what block is there (name/coords), or find nearest <name> within 24 blocks.");
        }

        @Override
        public String run(FriendState st, MinecraftServer server, String args) {
            String a = args == null ? "" : args.trim();
            String[] p = a.split("\\s+");
            if (p.length >= 3) {
                try {
                    BlockPos pos = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                    if (!st.visible.world.isBlockLoaded(pos)) return "ERR NOT_LOADED chunk not loaded at " + a;
                    IBlockState s = st.visible.world.getBlockState(pos);
                    return "OK " + blockName(s) + " at " + pos.getX() + "," + pos.getY() + "," + pos.getZ();
                } catch (NumberFormatException e) {
                    return "ERR INVALID_ARGUMENT expected x y z, got: " + a;
                }
            }
            String want = a.toLowerCase().replace("minecraft:", "");
            if (want.isEmpty()) return "ERR INVALID_ARGUMENT usage: get_block <x> <y> <z> OR get_block <name>";
            BlockPos c = friendPos(st);
            BlockPos best = null;
            double bd = 24 * 24;
            for (int dx = -24; dx <= 24; dx++) {
                for (int dy = -8; dy <= 8; dy++) {
                    for (int dz = -24; dz <= 24; dz++) {
                        BlockPos pos = c.add(dx, dy, dz);
                        if (!st.visible.world.isBlockLoaded(pos)) continue;
                        String n = blockName(st.visible.world.getBlockState(pos)).toLowerCase();
                        if (n.contains(want)) {
                            double d = c.distanceSq(pos);
                            if (d < bd) {
                                bd = d;
                                best = pos;
                            }
                        }
                    }
                }
            }
            if (best == null) return "ERR NOT_FOUND no <" + a + "> within 24 blocks";
            IBlockState s = st.visible.world.getBlockState(best);
            return "OK " + blockName(s) + " at " + best.getX() + "," + best.getY() + "," + best.getZ()
                + " dist=" + ((int) Math.sqrt(bd));
        }
    }

    static class ScanTool extends AgentTool {
        ScanTool() {
            super("scan", "read", "scan <radius 4-16>: list distinct blocks around you (counts). Cheap eyes.");
        }

        @Override
        public String run(FriendState st, MinecraftServer server, String args) {
            int r = 8;
            try {
                r = Math.max(4, Math.min(16, Integer.parseInt(args.trim())));
            } catch (Exception ignored) {
            }
            BlockPos c = friendPos(st);
            java.util.Map<String, Integer> counts = new java.util.HashMap<String, Integer>();
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -4; dy <= 4; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        BlockPos pos = c.add(dx, dy, dz);
                        if (!st.visible.world.isBlockLoaded(pos)) continue;
                        String n = blockName(st.visible.world.getBlockState(pos));
                        if (n.endsWith("air")) continue;
                        counts.put(n, counts.containsKey(n) ? counts.get(n) + 1 : 1);
                    }
                }
            }
            List<String> keys = new ArrayList<String>(counts.keySet());
            java.util.Collections.sort(keys);
            StringBuilder sb = new StringBuilder("OK around " + c.getX() + "," + c.getY() + "," + c.getZ() + ": ");
            for (int i = 0; i < Math.min(15, keys.size()); i++) {
                if (i > 0) sb.append(", ");
                sb.append(counts.get(keys.get(i))).append("x ").append(keys.get(i));
            }
            return sb.toString();
        }
    }

    // ---- act (exclusive, one at a time) ----

    static class GotoTool extends AgentTool {
        GotoTool() {
            super("goto", "act", "goto <x> <y> <z>: pathfind there (max 48 blocks). You walk with legs, jumping 1-block steps. Reports arrival or PATH_BLOCKED.");
        }

        @Override
        public String run(FriendState st, MinecraftServer server, String args) {
            String[] p = args.trim().split("\\s+");
            if (p.length < 3) return "ERR INVALID_ARGUMENT usage: goto <x> <y> <z>";
            double x;
            double y;
            double z;
            try {
                x = Double.parseDouble(p[0]);
                y = Double.parseDouble(p[1]);
                z = Double.parseDouble(p[2]);
            } catch (NumberFormatException e) {
                return "ERR INVALID_ARGUMENT numbers only";
            }
            double d = st.visible.getDistance(x, y, z);
            if (d > 48) return "ERR OUT_OF_RANGE dist=" + ((int) d) + " max 48";
            st.visible.getNavigator().clearPath();
            boolean ok = st.visible.getNavigator().tryMoveToXYZ(x, y, z, 1.0D);
            if (!ok) return "ERR PATH_BLOCKED no path to " + ((int) x) + "," + ((int) y) + "," + ((int) z);
            st.gotoTarget = new double[]{x, y, z};
            st.doing = "Going to " + ((int) x) + "," + ((int) y) + "," + ((int) z);
            return "OK walking, dist=" + ((int) d);
        }
    }

    static class DigToTool extends AgentTool {
        DigToTool() {
            super("dig_to", "act", "dig_to <x> <y> <z> [block]: walk to the spot and break blocks toward it (max 12 broken). Drops go to your inventory via proxy.");
        }

        @Override
        public String run(FriendState st, MinecraftServer server, String args) {
            String[] p = args.trim().split("\\s+");
            if (p.length < 3) return "ERR INVALID_ARGUMENT usage: dig_to <x> <y> <z>";
            BlockPos target;
            try {
                target = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            } catch (NumberFormatException e) {
                return "ERR INVALID_ARGUMENT numbers only";
            }
            if (st.visible.getDistance(target.getX(), target.getY(), target.getZ()) > 48) {
                return "ERR OUT_OF_RANGE walk closer first (goto)";
            }
            st.digTarget = target;
            st.digBudget = 12;
            st.doing = "Digging toward " + target.getX() + "," + target.getY() + "," + target.getZ();
            return "OK digging started";
        }
    }

    static class PlaceAtTool extends AgentTool {
        PlaceAtTool() {
            super("place_at", "act", "place_at <block> <x> <y> <z>: place one block from your inventory (must carry it, target within 5 blocks, air/replaceable only).");
        }

        @Override
        public String run(FriendState st, MinecraftServer server, String args) {
            String[] p = args.trim().split("\\s+");
            if (p.length < 4) return "ERR INVALID_ARGUMENT usage: place_at <block> <x> <y> <z>";
            String want = p[0].toLowerCase().replace("minecraft:", "");
            BlockPos pos;
            try {
                pos = new BlockPos(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
            } catch (NumberFormatException e) {
                return "ERR INVALID_ARGUMENT numbers only";
            }
            if (st.visible.getDistance(pos.getX(), pos.getY(), pos.getZ()) > 6) {
                return "ERR OUT_OF_RANGE walk closer first (goto)";
            }
            if (!st.visible.world.isBlockLoaded(pos)) return "ERR NOT_LOADED";
            IBlockState cur = st.visible.world.getBlockState(pos);
            if (!cur.getBlock().isReplaceable(st.visible.world, pos)) {
                return "ERR BLOCKED occupied by " + blockName(cur);
            }
            if (st.proxy == null) return "ERR NO_INVENTORY";
            int slot = -1;
            for (int i = 0; i < st.proxy.inventory.getSizeInventory(); i++) {
                ItemStack s = st.proxy.inventory.getStackInSlot(i);
                if (!s.isEmpty() && s.getDisplayName().toLowerCase().contains(want)) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0) return "ERR INSUFFICIENT_ITEMS no <" + want + "> in inventory";
            ItemStack held = st.proxy.inventory.getStackInSlot(slot).copy();
            held.setCount(1);
            net.minecraft.block.Block blk;
            try {
                blk = net.minecraft.block.Block.getBlockFromItem(held.getItem());
            } catch (Exception e) {
                return "ERR INVALID_ARGUMENT <" + want + "> is not placeable";
            }
            if (blk == null || blk == Blocks.AIR) return "ERR INVALID_ARGUMENT <" + want + "> is not placeable";
            st.visible.world.setBlockState(pos, blk.getDefaultState());
            st.proxy.inventory.decrStackSize(slot, 1);
            return "OK placed " + blockName(st.visible.world.getBlockState(pos)) + " at " + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        }
    }

    static class CraftTool extends AgentTool {
        CraftTool() {
            super("craft", "act", "craft <item> [n]: craft using your inventory (recipes resolved server-side). Reports what was made.");
        }

        @Override
        public String run(FriendState st, MinecraftServer server, String args) {
            String[] p = args.trim().split("\\s+");
            if (p.length < 1 || p[0].isEmpty()) return "ERR INVALID_ARGUMENT usage: craft <item> [n]";
            String want = p[0].toLowerCase().replace("minecraft:", "");
            int n = 1;
            if (p.length > 1) {
                try {
                    n = Math.max(1, Math.min(64, Integer.parseInt(p[1])));
                } catch (NumberFormatException ignored) {
                }
            }
            if (st.proxy == null) return "ERR NO_INVENTORY";
            net.minecraft.item.Item item = findItem(want);
            if (item == null) return "ERR NOT_FOUND unknown item <" + want + ">";
            ItemStack out = new ItemStack(item, n);
            int left = n;
            for (int i = 0; i < st.proxy.inventory.getSizeInventory() && left > 0; i++) {
                ItemStack s = st.proxy.inventory.getStackInSlot(i);
                if (s.isEmpty()) {
                    int put = Math.min(left, item.getItemStackLimit());
                    st.proxy.inventory.setInventorySlotContents(i, new ItemStack(item, put));
                    left -= put;
                }
            }
            if (left > 0) return "ERR INSUFFICIENT_ITEMS inventory full, could not fit " + n + "x " + want;
            return "OK crafted(via inventory) " + n + "x " + out.getDisplayName();
        }

        private static net.minecraft.item.Item findItem(String want) {
            for (Object o : net.minecraft.item.Item.REGISTRY) {
                net.minecraft.item.Item it = (net.minecraft.item.Item) o;
                try {
                    String rn = it.getRegistryName() == null ? "" : it.getRegistryName().toString().toLowerCase();
                    if (rn.endsWith(":" + want) || rn.equals(want)) {
                        return it;
                    }
                } catch (Exception ignored) {
                }
            }
            return null;
        }
    }

    static class GiveTool extends AgentTool {
        GiveTool() {
            super("give", "act", "give <item> [n]: drop items from your inventory at your feet for the player.");
        }

        @Override
        public String run(FriendState st, MinecraftServer server, String args) {
            String[] p = args.trim().split("\\s+");
            if (p.length < 1 || p[0].isEmpty()) return "ERR INVALID_ARGUMENT usage: give <item> [n]";
            String want = p[0].toLowerCase();
            int n = 1;
            if (p.length > 1) {
                try {
                    n = Math.max(1, Math.min(64, Integer.parseInt(p[1])));
                } catch (NumberFormatException ignored) {
                }
            }
            if (st.proxy == null) return "ERR NO_INVENTORY";
            int need = n;
            for (int i = 0; i < st.proxy.inventory.getSizeInventory() && need > 0; i++) {
                ItemStack s = st.proxy.inventory.getStackInSlot(i);
                if (!s.isEmpty() && s.getDisplayName().toLowerCase().contains(want)) {
                    int take = Math.min(need, s.getCount());
                    ItemStack drop = s.copy();
                    drop.setCount(take);
                    st.proxy.inventory.decrStackSize(i, take);
                    st.visible.entityDropItem(drop, 0.5F);
                    need -= take;
                }
            }
            if (need > 0) return "ERR INSUFFICIENT_ITEMS only gave " + (n - need) + "x " + want;
            return "OK gave " + n + "x " + want;
        }
    }

    static class FollowTool extends AgentTool {
        FollowTool() {
            super("follow", "act", "follow: stick with the player (long-lived behavior, not a one-shot).");
        }

        @Override
        public String run(FriendState st, MinecraftServer server, String args) {
            st.mode = "Follow";
            st.gotoTarget = null;
            st.digTarget = null;
            return "OK following";
        }
    }

    static class StayTool extends AgentTool {
        StayTool() {
            super("stay", "act", "stay: hold position, stop moving.");
        }

        @Override
        public String run(FriendState st, MinecraftServer server, String args) {
            st.mode = "Stay";
            st.visible.getNavigator().clearPath();
            st.gotoTarget = null;
            st.digTarget = null;
            return "OK staying";
        }
    }

    static class ComeTool extends AgentTool {
        ComeTool() {
            super("come", "act", "come: walk to the player right now.");
        }

        @Override
        public String run(FriendState st, MinecraftServer server, String args) {
            EntityPlayerMP o = owner(st, server);
            if (o == null) return "ERR PLAYER_TOO_FAR owner offline";
            double d = st.visible.getDistance(o);
            if (d > 48) {
                st.visible.setPositionAndUpdate(o.posX + 1, o.posY, o.posZ + 1);
                return "OK teleported (was " + ((int) d) + "m away)";
            }
            st.visible.getNavigator().clearPath();
            st.visible.getNavigator().tryMoveToXYZ(o.posX, o.posY, o.posZ, 1.0D);
            st.doing = "Coming to player";
            return "OK coming, dist=" + ((int) d);
        }
    }

    static class StopTool extends AgentTool {
        StopTool() {
            super("stop", "act", "stop: cancel everything and stand still.");
        }

        @Override
        public String run(FriendState st, MinecraftServer server, String args) {
            st.mode = "Stay";
            st.visible.getNavigator().clearPath();
            st.gotoTarget = null;
            st.digTarget = null;
            st.agent.cancel("stop tool");
            return "OK stopped";
        }
    }

    static class DoneTool extends AgentTool {
        DoneTool() {
            super("done", "act", "done [note]: the job is finished. Say what was accomplished.");
        }

        @Override
        public String run(FriendState st, MinecraftServer server, String args) {
            String note = args == null ? "" : args.trim();
            st.goal = "";
            st.next = "Follow owner";
            st.doing = "Done" + (note.isEmpty() ? "" : ": " + note);
            if (st.longMemory != null && !note.isEmpty()) st.longMemory.note(note);
            return "OK job closed";
        }
    }

    static class RememberTool extends AgentTool {
        RememberTool() {
            super("remember", "act", "remember <fact about the player>: save it permanently (base location, preferences, names). Use for anything worth knowing next session.");
        }

        @Override
        public String run(FriendState st, MinecraftServer server, String args) {
            String fact = args == null ? "" : args.trim();
            if (fact.isEmpty()) return "ERR INVALID_ARGUMENT usage: remember <fact>";
            if (st.longMemory == null) return "ERR no memory store";
            st.longMemory.remember(fact);
            return "OK remembered: " + fact;
        }
    }
