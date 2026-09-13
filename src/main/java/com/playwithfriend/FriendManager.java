package com.playwithfriend;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.util.FakePlayerFactory;

public class FriendManager {
    private final PlayWithFriend mod;
    private final Map<UUID, FriendState> friends = new ConcurrentHashMap<UUID, FriendState>();
    private ForgeChunkManager.Ticket ticket;

    public FriendManager(PlayWithFriend mod) {
        this.mod = mod;
    }

    public Collection<FriendState> all() {
        return friends.values();
    }

    public FriendState spawn(MinecraftServer server, EntityPlayerMP owner, String name) {
        WorldServer world = PlayWithFriend.overworld(server);
        if (world.isRemote) return null;
        for (FriendState f : friends.values()) {
            if (f.ownerId != null && f.ownerId.equals(owner.getUniqueID()) && f.name.equalsIgnoreCase(name)) return f;
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
        return st;
    }

    public FriendState spawn(MinecraftServer server, String name) {
        EntityPlayerMP owner = server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().get(0);
        return spawn(server, owner, name);
    }

    public void despawn(MinecraftServer server, UUID id) {
        FriendState st = friends.remove(id);
        if (st != null) {
            if (st.visible != null) st.visible.world.removeEntity(st.visible);
            releaseTicket();
        }
    }

    private void ensureTicket(WorldServer world, EntityFriend vis) {
        if (ticket == null) {
            ticket = ForgeChunkManager.requestTicket(PlayWithFriend.instance, world, ForgeChunkManager.Type.ENTITY);
        }
        if (ticket != null) {
            ForgeChunkManager.bindEntity(world, ticket, vis);
        }
    }

    private void releaseTicket() {
        if (friends.isEmpty() && ticket != null) {
            ForgeChunkManager.releaseTicket(ticket);
            ticket = null;
        }
    }

    public void tick(MinecraftServer server) {
        for (FriendState st : friends.values()) {
            if (st.visible == null || st.visible.isDead) continue;
            if (st.visible.dimension != 0) continue;
            EntityPlayerMP owner = server.getPlayerList().getPlayerByUUID(st.ownerId);
            if (owner == null) {
                st.doing = "Waiting for owner (offline)";
                continue;
            }
            if (owner.dimension != st.visible.dimension) {
                st.doing = "Waiting (different dimension)";
                continue;
            }
            if ("Follow".equals(st.mode)) {
                double d = st.visible.getDistance(owner);
                if (d > 3.0D && d < 64.0D) {
                    st.doing = "Following owner (" + (int) d + "m)";
                    if (st.visible.getNavigator().noPath()) {
                        st.visible.getNavigator().tryMoveToXYZ(owner.posX, owner.posY, owner.posZ, 1.0D);
                    }
                } else if (d >= 64.0D) {
                    st.doing = "Teleporting to owner (too far)";
                    st.visible.getNavigator().clearPath();
                    st.visible.setPositionAndUpdate(owner.posX + 1, owner.posY, owner.posZ + 1);
                } else {
                    st.doing = "Idle near owner";
                }
            }
            if (st.proxy != null) {
                st.proxy.setPosition(st.visible.posX, st.visible.posY, st.visible.posZ);
            }
            st.brain.tickAsync(st, server);
            st.executor.tick(st, server);
        }
    }
}
