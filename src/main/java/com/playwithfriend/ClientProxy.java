package com.playwithfriend;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Renders the friend as an actual player model with a Mojang skin looked up
 * by name (MHF_Steve default, per-friend name after). Downloads + caches the
 * real player texture; falls back to default Steve/Alex while loading.
 */
@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {
    private static final Map<String, ResourceLocation> SKINS = new HashMap<String, ResourceLocation>();

    @Override
    public void preInit(FMLPreInitializationEvent e) {
        super.preInit(e);
        RenderingRegistry.registerEntityRenderingHandler(EntityFriend.class,
            (RenderManager m) -> new RenderBiped<EntityFriend>(m, new ModelBiped(), 0.5F) {
                @Override
                protected ResourceLocation getEntityTexture(EntityFriend f) {
                    return skinFor(f.getSkinName(), f.getUniqueID());
                }
            });
    }

    private static ResourceLocation skinFor(String name, java.util.UUID id) {
        try {
            ResourceLocation cached = SKINS.get(name);
            if (cached != null) return cached;
            Minecraft mc = Minecraft.getMinecraft();
            GameProfile profile = new GameProfile(null, name);
            MinecraftProfileTexture tex = mc.getSkinManager().loadSkinFromCache(profile);
            ResourceLocation loc;
            if (tex != null) {
                loc = new ResourceLocation("skins/" + tex.getHash());
                ITextureObject obj = new ThreadDownloadImageData(null, tex.getUrl(), DefaultPlayerSkin.getDefaultSkin(id), new net.minecraft.client.resources.SkinManager.SkinAvailableCallback() {
                    @Override
                    public void skinAvailable(MinecraftProfileTexture.Type type, ResourceLocation l, MinecraftProfileTexture t) {
                    }
                });
                mc.getTextureManager().loadTexture(loc, obj);
            } else {
                loc = DefaultPlayerSkin.getDefaultSkin(id);
            }
            SKINS.put(name, loc);
            return loc;
        } catch (Exception e) {
            try {
                return DefaultPlayerSkin.getDefaultSkin(id);
            } catch (Exception e2) {
                return AbstractClientPlayer.TEXTURE_STEVE;
            }
        }
    }
}
