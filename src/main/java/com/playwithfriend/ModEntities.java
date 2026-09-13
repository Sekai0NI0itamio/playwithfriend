package com.playwithfriend;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.EntityRegistry;

public class ModEntities {
    public static void register() {
        EntityRegistry.registerModEntity(
            new ResourceLocation(PlayWithFriend.MODID, "friend"),
            EntityFriend.class, "Friend", 0, PlayWithFriend.instance, 64, 3, true);
    }
}
