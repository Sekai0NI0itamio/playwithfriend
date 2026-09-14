package com.playwithfriend;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

@Mod(modid = PlayWithFriend.MODID, name = PlayWithFriend.NAME, version = "1.0.0", acceptableRemoteVersions = "*")
public class PlayWithFriend {
    public static final String MODID = "playwithfriend";
    public static final String NAME = "Play With Friend";

    @Mod.Instance
    public static PlayWithFriend instance;

    @SidedProxy(clientSide = "com.playwithfriend.ClientProxy", serverSide = "com.playwithfriend.CommonProxy")
    public static CommonProxy proxy;

    public FriendManager friends;
    public HermesConfig hermes;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        hermes = new HermesConfig(e.getSuggestedConfigurationFile().getParentFile());
        friends = new FriendManager(this);
        proxy.preInit(e);
        MinecraftForge.EVENT_BUS.register(new FriendEvents(this));
        MinecraftForge.EVENT_BUS.register(new FriendChat(this));
        MinecraftForge.EVENT_BUS.register(new GuiTitleHandler(this));
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
    }

    @Mod.EventHandler
    public void serverStart(FMLServerStartingEvent e) {
        e.registerServerCommand(new FriendCommand(this));
    }

    public static WorldServer overworld(MinecraftServer s) {
        return s.getWorld(0);
    }
}
