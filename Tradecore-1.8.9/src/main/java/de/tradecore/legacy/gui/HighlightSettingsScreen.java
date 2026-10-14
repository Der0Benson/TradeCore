package de.tradecore.legacy.gui;

import de.tradecore.legacy.config.ConfigManager;
import de.tradecore.legacy.config.ModConfig;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public final class HighlightSettingsScreen extends BaseScreen {
    private final GuiScreen parent;
    private final ModConfig config = ConfigManager.get();
    public HighlightSettingsScreen(GuiScreen parent) { this.parent = parent; }

    @Override public void initGui() {
        buttonList.clear();
        int x = centerX(220), y = 55;
        buttonList.add(new GuiButton(1, x, y, 220, 20, "Farbe: " + config.highlightColorHex));
        buttonList.add(new GuiButton(2, x, y + 28, 220, 20, "Reichweite: " + config.highlightDistance));
        buttonList.add(new GuiButton(3, x, y + 56, 220, 20, "Neu scannen: " + config.highlightRescanSeconds + "s"));
        buttonList.add(new GuiButton(4, x, y + 84, 220, 20, "Partikelmenge: " + config.highlightParticleAmount + "x"));
        buttonList.add(new GuiButton(0, x + 60, y + 122, 100, 20, "Fertig"));
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0) { ConfigManager.save(); mc.displayGuiScreen(parent); return; }
        if (button.id == 1) { mc.displayGuiScreen(new HighlightColorScreen(this)); return; }
        if (button.id == 2) config.highlightDistance = next(config.highlightDistance, new int[]{8, 16, 24, 32, 48, 64, 96, 128});
        if (button.id == 3) config.highlightRescanSeconds = next(config.highlightRescanSeconds, new int[]{5, 10, 15, 20, 30, 45, 60});
        if (button.id == 4) config.highlightParticleAmount = config.highlightParticleAmount >= 5 ? 1 : config.highlightParticleAmount + 1;
        ConfigManager.save();
        initGui();
    }

    private static int next(int current, int[] values) {
        for (int value : values) if (value > current) return value;
        return values[0];
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawTradecoreBackground("BdT-Highlight konfigurieren");
        drawCenteredString(fontRendererObj, "Klicke eine Einstellung, um den nächsten Wert zu wählen.", width / 2, height - 41, 0xFFAAAAAA);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
