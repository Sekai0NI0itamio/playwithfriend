package com.playwithfriend;

import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiHermesConfig extends GuiScreen {
    private final GuiScreen parent;
    private final HermesConfig cfg;
    private GuiTextField url;
    private GuiTextField key;
    private GuiTextField model;

    public GuiHermesConfig(GuiScreen parent, HermesConfig cfg) {
        this.parent = parent;
        this.cfg = cfg;
    }

    @Override
    public void initGui() {
        buttonList.add(new GuiButton(1, width / 2 - 100, height - 40, "Save"));
        buttonList.add(new GuiButton(0, width / 2 - 100, height - 65, "Cancel"));
        url = new GuiTextField(10, fontRenderer, width / 2 - 150, 40, 300, 20);
        key = new GuiTextField(11, fontRenderer, width / 2 - 150, 80, 300, 20);
        model = new GuiTextField(12, fontRenderer, width / 2 - 150, 120, 300, 20);
        url.setText(cfg.baseUrl);
        model.setText(cfg.model);
        key.setText(cfg.apiKey);
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id == 1) {
            cfg.baseUrl = url.getText().trim();
            cfg.apiKey = key.getText().trim();
            cfg.model = model.getText().trim();
            cfg.save();
            mc.displayGuiScreen(parent);
        } else if (b.id == 0) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void keyTyped(char c, int k) throws IOException {
        super.keyTyped(c, k);
        url.textboxKeyTyped(c, k);
        key.textboxKeyTyped(c, k);
        model.textboxKeyTyped(c, k);
    }

    @Override
    protected void mouseClicked(int x, int y, int b) throws IOException {
        super.mouseClicked(x, y, b);
        url.mouseClicked(x, y, b);
        key.mouseClicked(x, y, b);
        model.mouseClicked(x, y, b);
    }

    @Override
    public void drawScreen(int x, int y, float t) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, "Hermes login (stored locally, never logged)", width / 2, 15, 0xFFFFFF);
        drawString(fontRenderer, "Base URL", width / 2 - 150, 30, 0xAAAAAA);
        drawString(fontRenderer, "API key", width / 2 - 150, 70, 0xAAAAAA);
        drawString(fontRenderer, "Model", width / 2 - 150, 110, 0xAAAAAA);
        url.drawTextBox();
        key.drawTextBox();
        model.drawTextBox();
        super.drawScreen(x, y, t);
    }
}
