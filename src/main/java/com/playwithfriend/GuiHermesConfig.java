package com.playwithfriend;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Hermes connect screen (no API-key typing):
 * 1. "Connect Hermes account" -> device-code request (background) ->
 *    shows code + opens portal.nousresearch.com in browser.
 * 2. Polls for approval -> stores OAuth tokens locally -> loads catalog.
 * 3. Model list with All/Free/Paid filter; each row shows FREE/PAID,
 *    price in/out per 1M, context length, input/output modalities.
 * 4. Select a model -> Save. Harness (DeepSeek) indicator shown.
 */
@SideOnly(Side.CLIENT)
public class GuiHermesConfig extends GuiScreen {
    private final GuiScreen parent;
    private final HermesConfig cfg;
    private final ModelCatalog catalog = new ModelCatalog();

    private GuiButton connectBtn;
    private GuiButton openBrowserBtn;
    private GuiButton filterBtn;
    private GuiButton saveBtn;
    private GuiButton backBtn;
    private GuiButton disconnectBtn;
    private ModelList list;

    private volatile String phase = "idle";
    private volatile String info = "";
    private volatile HermesPortal.DeviceAuth device;
    private volatile boolean polling;
    private volatile int pollCountdown = 0;
    private int filter = 0;
    private int selected = -1;

    public GuiHermesConfig(GuiScreen parent, HermesConfig cfg) {
        this.parent = parent;
        this.cfg = cfg;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        connectBtn = new GuiButton(1, cx - 210, 52, 200, 20, cfg.hasOAuth() ? "Connected - reconnect" : "Connect Hermes account");
        openBrowserBtn = new GuiButton(2, cx + 10, 52, 200, 20, "Open login page");
        openBrowserBtn.enabled = device != null;
        String[] filters = new String[]{"Filter: All", "Filter: Free only", "Filter: Paid only"};
        filterBtn = new GuiButton(3, cx - 210, 76, 200, 20, filters[filter]);
        saveBtn = new GuiButton(4, cx + 10, 76, 200, 20, "Save model");
        saveBtn.enabled = selected >= 0;
        disconnectBtn = new GuiButton(5, cx - 210, height - 48, 200, 20, "Disconnect");
        disconnectBtn.enabled = cfg.hasOAuth();
        backBtn = new GuiButton(0, cx + 10, height - 48, 200, 20, "Back");
        buttonList.add(connectBtn);
        buttonList.add(openBrowserBtn);
        buttonList.add(filterBtn);
        buttonList.add(saveBtn);
        buttonList.add(disconnectBtn);
        buttonList.add(backBtn);
        list = new ModelList();
        list.registerScrollButtons(10, 11);
        if (cfg.hasOAuth() && catalog.entries.isEmpty() && catalog.status.isEmpty()) {
            catalog.refresh(cfg);
            phase = "loading";
        }
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id == 0) {
            mc.displayGuiScreen(parent);
        } else if (b.id == 1) {
            startConnect();
        } else if (b.id == 2) {
            if (device != null) HermesPortal.openBrowser(device.verifyUrl);
        } else if (b.id == 3) {
            filter = (filter + 1) % 3;
            String[] filters = new String[]{"Filter: All", "Filter: Free only", "Filter: Paid only"};
            filterBtn.displayString = filters[filter];
            selected = -1;
            saveBtn.enabled = false;
        } else if (b.id == 4) {
            List<ModelCatalog.Entry> vis = visible();
            if (selected >= 0 && selected < vis.size()) {
                cfg.model = vis.get(selected).id;
                cfg.save();
                info = "Saved model: " + cfg.model;
                connectBtn.displayString = "Connected - reconnect";
                disconnectBtn.enabled = true;
            }
        } else if (b.id == 5) {
            cfg.clearOAuth();
            catalog.entries.clear();
            selected = -1;
            info = "Disconnected.";
            connectBtn.displayString = "Connect Hermes account";
            disconnectBtn.enabled = false;
        }
    }

    private void startConnect() {
        phase = "code";
        info = "Contacting Nous Portal...";
        device = null;
        polling = false;
        openBrowserBtn.enabled = false;
        new Thread(() -> {
            try {
                HermesPortal.DeviceAuth d = HermesPortal.requestDeviceCode(cfg.portalUrl, cfg.clientId, HermesConfig.DEFAULT_SCOPE);
                device = d;
                info = "Approve in your browser, then wait.";
                phase = "poll";
                HermesPortal.openBrowser(d.verifyUrl);
                openBrowserBtn.enabled = true;
                polling = true;
                long deadline = System.currentTimeMillis() + d.expiresIn * 1000L;
                while (polling && System.currentTimeMillis() < deadline) {
                    try {
                        HermesPortal.Tokens t = HermesPortal.pollOnce(cfg.portalUrl, cfg.clientId, d.deviceCode);
                        if (t != null) {
                            cfg.accessToken = t.accessToken;
                            if (!t.refreshToken.isEmpty()) cfg.refreshToken = t.refreshToken;
                            if (!t.tokenType.isEmpty()) cfg.tokenType = t.tokenType;
                            if (t.expiresInSec > 0) cfg.expiresAt = System.currentTimeMillis() + t.expiresInSec * 1000L;
                            if (!t.inferenceBaseUrl.isEmpty()) cfg.inferenceUrl = t.inferenceBaseUrl;
                            cfg.save();
                            try {
                                HermesPortal.Account a = HermesPortal.account(cfg.portalUrl, cfg.accessToken);
                                cfg.knownFreeTier = a.freeTier;
                                cfg.save();
                            } catch (Exception ignored) {
                            }
                            polling = false;
                            phase = "loading";
                            info = "Connected! Loading models...";
                            catalog.refresh(cfg);
                            return;
                        }
                    } catch (Exception e) {
                        info = "Waiting for approval... (" + e.getMessage() + ")";
                    }
                    try { Thread.sleep(Math.max(1000, d.interval * 1000)); } catch (InterruptedException ignored) { return; }
                }
                if (polling) {
                    polling = false;
                    phase = "idle";
                    info = "Login timed out. Click Connect to try again.";
                }
            } catch (Exception e) {
                phase = "idle";
                info = "Connect failed: " + e.getMessage();
            }
        }).start();
    }

    private List<ModelCatalog.Entry> visible() {
        List<ModelCatalog.Entry> out = new ArrayList<ModelCatalog.Entry>();
        for (ModelCatalog.Entry e : catalog.entries) {
            if (filter == 1 && !e.free) continue;
            if (filter == 2 && e.free) continue;
            out.add(e);
        }
        return out;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        // Recompute every tick so buttons never look dead/stale after
        // background connect/catalog threads change state.
        openBrowserBtn.enabled = device != null;
        disconnectBtn.enabled = cfg.hasOAuth();
        connectBtn.displayString = polling || "code".equals(phase) || "poll".equals(phase)
            ? "Waiting for approval..."
            : (cfg.hasOAuth() ? "Connected - reconnect" : "Connect Hermes account");
        List<ModelCatalog.Entry> vis = visible();
        if (selected < 0 || selected >= vis.size()) {
            selected = -1;
            saveBtn.enabled = false;
        } else {
            saveBtn.enabled = true;
        }
        if ("loading".equals(phase) && !catalog.status.startsWith("Loading")) {
            phase = "done";
            info = catalog.status;
            if (!cfg.model.isEmpty()) {
                for (int i = 0; i < vis.size(); i++) {
                    if (vis.get(i).id.equals(cfg.model)) selected = i;
                }
                saveBtn.enabled = selected >= 0;
            }
        } else if ("poll".equals(phase) && device == null) {
            pollCountdown++;
        }
        if (!catalog.status.isEmpty() && "done".equals(phase)) info = catalog.status;
    }

    @Override
    public void drawScreen(int x, int y, float t) {
        drawDefaultBackground();
        int cx = width / 2;
        drawCenteredString(fontRenderer, "Hermes account (OAuth connect, token stays local)", cx, 12, 0xFFFFFF);
        String statusLine = cfg.hasOAuth()
            ? "Connected" + (cfg.knownFreeTier != null ? (cfg.knownFreeTier ? " - FREE tier" : " - paid") : "")
              + (cfg.model.isEmpty() ? " - pick a model below" : " - " + cfg.model)
            : "Not connected";
        drawCenteredString(fontRenderer, statusLine, cx, 26, cfg.hasOAuth() ? 0x7CFC00 : 0xFFAA55);
        if ("code".equals(phase) || "poll".equals(phase)) {
            if (device != null) {
                drawCenteredString(fontRenderer, "Code: " + device.userCode, cx, 40, 0xFFFF55);
                String url = device.verifyUrl;
                if (url.length() > 76) url = url.substring(0, 76) + "...";
                drawCenteredString(fontRenderer, "Approve at: " + url, cx, 92, 0x7CC4FF);
            } else {
                drawCenteredString(fontRenderer, info, cx, 40, 0xAAAAAA);
            }
        }
        if (list != null) list.drawScreen(x, y, t);
        int hy = 100 + Math.min(visible().size(), 8) * 22 + 8;
        String harness = "Tool/plan harness: " + (cfg.harnessEnabled ? ("DeepSeek (" + cfg.harnessModel + ")") : "off")
            + " - cheap pass parses plans so " + (cfg.model.isEmpty() ? "your model" : cfg.model) + " only plans.";
        drawCenteredString(fontRenderer, harness, cx, Math.min(hy, height - 62), 0x888888);
        if (!info.isEmpty() && !"code".equals(phase) && !"poll".equals(phase)) {
            drawCenteredString(fontRenderer, info.length() > 100 ? info.substring(0, 100) : info, cx, Math.min(hy + 10, height - 52), 0xAAAAAA);
        }
        super.drawScreen(x, y, t);
    }

    @Override
    public void onGuiClosed() {
        polling = false;
    }

    @Override
    protected void keyTyped(char c, int k) throws IOException {
        super.keyTyped(c, k);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (list != null) list.handleMouseInput();
    }

    // NOTE: GuiSlot has no mouseClicked/mouseReleased in 1.12.2 — all slot
    // mouse handling (scroll wheel, scrollbar drag, row click) runs through
    // handleMouseInput(), forwarded above. Do not add direct forwards here.

    class ModelList extends GuiSlot {
        ModelList() {
            super(GuiHermesConfig.this.mc, GuiHermesConfig.this.width, GuiHermesConfig.this.height, 100, GuiHermesConfig.this.height - 70, 22);
        }

        @Override
        protected int getSize() {
            return visible().size();
        }

        @Override
        protected void elementClicked(int i, boolean dbl, int mx, int my) {
            selected = i;
            saveBtn.enabled = true;
            if (dbl) actionPerformed(saveBtn);
        }

        @Override
        protected boolean isSelected(int i) {
            return i == selected;
        }

        @Override
        protected void drawBackground() {
        }

        @Override
        protected void drawSlot(int i, int rx, int ry, int rh, int mx, int my, float t) {
            List<ModelCatalog.Entry> vis = visible();
            if (i < 0 || i >= vis.size()) return;
            ModelCatalog.Entry e = vis.get(i);
            String marker = (i == selected) ? "[x] " : (cfg.model.equals(e.id) ? "> " : "[ ] ");
            String name = marker + (e.label.isEmpty() ? e.id : e.label);
            if (name.length() > 52) name = name.substring(0, 52);
            GuiHermesConfig.this.drawString(fontRenderer, name, rx + 4, ry + 1, e.free ? 0x7CFC00 : 0xFFD27C);
            String d = e.detail;
            if (d.length() > 80) d = d.substring(0, 80);
            GuiHermesConfig.this.drawString(fontRenderer, d, rx + 4, ry + 11, 0x999999);
        }
    }
}
