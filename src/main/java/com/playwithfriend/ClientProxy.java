package com.playwithfriend;

import com.mojang.authlib.GameProfile;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Renders the friend as an actual player model with the owner's skin flavor:
 * each friend gets a GameProfile named "PWF_<friend>" so the skin pipeline
 * resolves a real player texture (cached per friend, falls back to default
 * Steve/Alex). No more purple-black box.
 */
@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {
    private static final Map<String, NetworkPlayerInfo> INFO = new HashMap<String, NetworkPlayerInfo>();

    @Override
    public void preInit(FMLPreInitializationEvent e) {
        super.preInit(e);
        RenderingRegistry.registerEntityRenderingHandler(EntityFriend.class,
            (RenderManager m) -> new RenderBiped<EntityFriend>(m, new ModelBiped(), 0.5F) {
                @Override
                protected ResourceLocation getEntityTexture(EntityFriend f) {
                    return skinFor(f);
                }
            });
    }

    private static ResourceLocation skinFor(EntityFriend f) {
        try {
            String key = f.getSkinName() + "|" + f.getUniqueID();
            NetworkPlayerInfo info = INFO.get(key);
            if (info == null) {
                GameProfile profile = new GameProfile(f.getUniqueID(), f.getSkinName());
                info = new NetworkPlayerInfo(profile);
                AbstractClientPlayer.registerSkin(info, net.minecraft.client.Minecraft.getMinecraft().getSessionService());
                INFO.put(key, info);
            }
            ResourceLocation loc = info.getLocationSkin();
            if (loc != null) return loc;
        } catch (Exception ignored) {
        }
        try {
            return DefaultPlayerSkin.getDefaultSkin(f.getUniqueID());
        } catch (Exception e) {
            return AbstractClientPlayer.TEXTURE_STEVE;
        }
    }
}
