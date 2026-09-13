package com.playwithfriend;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiTitleHandler {
    private final PlayWithFriend mod;

    public GuiTitleHandler(PlayWithFriend mod) {
        this.mod = mod;
    }

    @SubscribeEvent
    public void onGui(GuiScreenEvent.InitGuiEvent.Post e) {
        GuiScreen g = e.getGui();
        if (g instanceof GuiMainMenu) {
            e.getButtonList().add(new GuiButton(9871, 10, 10, 120, 20, "Hermes: " + (mod.hermes.hasKey() ? mod.hermes.model : "login")));
        }
    }

    @SubscribeEvent
    public void onClick(GuiScreenEvent.ActionPerformedEvent.Post e) {
        if (e.getGui() instanceof GuiMainMenu && e.getButton().id == 9871) {
            e.getGui().mc.displayGuiScreen(new GuiHermesConfig(e.getGui(), mod.hermes));
        }
    }
}
