package com.playwithfriend;

import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {
    public void preInit(FMLPreInitializationEvent e) {
        ModEntities.register();
    }
}
