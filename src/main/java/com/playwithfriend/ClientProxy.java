package com.playwithfriend;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {
    @Override
    public void preInit(FMLPreInitializationEvent e) {
        super.preInit(e);
        RenderingRegistry.registerEntityRenderingHandler(EntityFriend.class,
            (RenderManager m) -> new RenderBiped<EntityFriend>(m, new ModelBiped(), 0.5F) {
                @Override
                protected ResourceLocation getEntityTexture(EntityFriend e) {
                    return new ResourceLocation("textures/entity/steve.png");
                }
            });
    }
}
