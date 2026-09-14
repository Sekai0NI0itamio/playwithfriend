package com.playwithfriend;

import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Renders the friend with the real player model (ModelPlayer: correct slim
 * UVs + hat/jacket layers) and a deterministic default skin per friend
 * (Steve/Alex by UUID hash). Always valid, works offline — no downloads at
 * render time, so no black/stretched corruption.
 */
@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {
    @Override
    public void preInit(FMLPreInitializationEvent e) {
        super.preInit(e);
        RenderingRegistry.registerEntityRenderingHandler(EntityFriend.class,
            (RenderManager m) -> new RenderLiving<EntityFriend>(m, new ModelPlayer(0.0F, false), 0.5F) {
                @Override
                protected ResourceLocation getEntityTexture(EntityFriend f) {
                    return DefaultPlayerSkin.getDefaultSkin(f.getUniqueID());
                }
            });
    }
}
