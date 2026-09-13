package com.playwithfriend;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class FriendEvents {
    private final PlayWithFriend mod;

    public FriendEvents(PlayWithFriend mod) {
        this.mod = mod;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (s == null) return;
        mod.friends.tick(s);
    }
}
