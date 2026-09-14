package com.playwithfriend;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import java.util.List;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.util.FakePlayerFactory;

public class FriendManager {
    private final PlayWithFriend mod;
    private final Map<UUID, FriendState> friends = new ConcurrentHashMap<UUID, FriendState>();

    public static class TicketCB implements ForgeChunkManager.LoadingCallback {
        @Override
        public void ticketsLoaded(List<ForgeChunkManager.Ticket> tickets, World world) {
        }
    }

    public FriendManager(PlayWithFriend mod) {
        this.mod = mod;
        ForgeChunkManager.setForcedChunkLoadingCallback(PlayWithFriend.instance, new TicketCB());
    }

    public Collection<FriendState> all() {
        return friends.values();
    }

    public static class SpawnResult {
        public FriendState state;
        public boolean fresh;
    }

    public FriendState spawn(MinecraftServer server, EntityPlayerMP owner, String name) {
        return spawnEx(server, owner, name).state;
    }

    public SpawnResult spawnEx(MinecraftServer server, EntityPlayerMP owner, String name) {
        SpawnResult r = new SpawnResult();
        WorldServer world = PlayWithFriend.overworld(server);
        if (world.isRemote) return r;
        for (FriendState f : friends.values()) {
            if (f.ownerId != null && f.ownerId.equals(owner.getUniqueID()) && f.name.equalsIgnoreCase(name)) {
                r.state = f;
                r.fresh = false;
                return r;
            }
        }
        BlockPos at = owner != null ? owner.getPosition() : world.getSpawnPoint();
        EntityFriend vis = new EntityFriend(world);
        vis.setPosition(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        vis.setCustomNameTag(name == null ? "Friend" : name);
        vis.setAlwaysRenderNameTag(true);
        if (owner != null) vis.setOwnerId(owner.getUniqueID());
        world.spawnEntity(vis);
        GameProfile profile = new GameProfile(UUID.randomUUID(), vis.getCustomNameTag());
        EntityPlayerMP proxy = FakePlayerFactory.get(world, profile);
        proxy.setPosition(vis.posX, vis.posY, vis.posZ);
        FriendState st = new FriendState(UUID.randomUUID(), vis.getCustomNameTag(), new FriendBrain(mod.hermes), new ActionExecutor());
        st.visible = vis;
        st.proxy = proxy;
        st.ownerId = owner != null ? owner.getUniqueID() : null;
        st.goal = "Follow";
        st.doing = "Spawned";
        st.next = "Follow owner";
        friends.put(st.id, st);
        ensureTicket(world, vis);
        r.state = st;
        r.fresh = true;
        return r;
    }

    public FriendState spawn(MinecraftServer server, String name) {
        EntityPlayerMP owner = server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().get(0);
        return spawn(server, owner, name);
    }

    public void despawn(MinecraftServer server, UUID id) {
        FriendState st = friends.remove(id);
        if (st != null) {
            st.agent.cancel("despawned");
            if (st.visible != null) st.visible.world.removeEntity(st.visible);
            releaseTicket(st);
        }
    }

    private void ensureTicket(WorldServer world, EntityFriend vis) {
        FriendState owner = null;
        for (FriendState st : friends.values()) {
            if (st.visible == vis) {
                owner = st;
                break;
            }
        }
        if (owner != null && owner.ticket == null) {
            owner.ticket = ForgeChunkManager.requestTicket(PlayWithFriend.instance, world, ForgeChunkManager.Type.NORMAL);
        }
        if (owner != null && owner.ticket != null) {
            ChunkPos pos = new ChunkPos(vis.getPosition());
            ForgeChunkManager.forceChunk(owner.ticket, pos);
            owner.chunkX = pos.x;
            owner.chunkZ = pos.z;
        }
    }

    private void releaseTicket(FriendState st) {
        if (st.ticket != null) {
            ForgeChunkManager.releaseTicket(st.ticket);
            st.ticket = null;
        }
    }

    public void tick(MinecraftServer server) {
        for (FriendState st : friends.values()) {
            if (st.visible == null || st.visible.isDead) continue;
            if (st.visible.dimension != 0) continue;
            EntityPlayerMP owner = st.ownerId == null ? null : server.getPlayerList().getPlayerByUUID(st.ownerId);
            if (owner == null) {
                st.doing = "Waiting for owner (offline)";
                continue;
            }
            if (owner.dimension != st.visible.dimension) {
                st.doing = "Waiting (different dimension)";
                continue;
            }
            // Follow runs inside EntityFriend's AI (swim + follow + wander +
            // watch + idle with step-1 blocks, jumping, head turning).
            // The tick only mirrors mode + handles the lost case (>32m).
            if (st.visible != null) st.visible.setFollowMode(st.mode);
            if ("Follow".equals(st.mode) && !st.agent.hasWork()) {
                double d = st.visible.getDistance(owner);
                if (d > 32.0D) {
                    st.doing = "Catching up (too far)";
                    st.visible.getNavigator().clearPath();
                    st.visible.setPositionAndUpdate(owner.posX + 1, owner.posY, owner.posZ + 1);
                } else if (d > 4.0D) {
                    st.doing = "Following owner (" + (int) d + "m)";
                } else {
                    st.doing = "Idle near owner";
                }
            }
            if (st.proxy != null) {
                st.proxy.setPosition(st.visible.posX, st.visible.posY, st.visible.posZ);
            }
            if (st.ticket != null && server.getTickCounter() % 100 == 0) {
                ChunkPos cur = new ChunkPos(st.visible.getPosition());
                if (cur.x != st.chunkX || cur.z != st.chunkZ) {
                    ForgeChunkManager.unforceChunk(st.ticket, new ChunkPos(st.chunkX, st.chunkZ));
                    ForgeChunkManager.forceChunk(st.ticket, cur);
                    st.chunkX = cur.x;
                    st.chunkZ = cur.z;
                }
            }
            st.brain.tickAsync(st, server);
            st.executor.tick(st, server);
        }
    }
}
