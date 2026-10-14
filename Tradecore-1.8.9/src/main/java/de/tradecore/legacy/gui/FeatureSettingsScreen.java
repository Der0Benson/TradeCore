package de.tradecore.legacy.gui;

import de.tradecore.legacy.config.ConfigManager;
import de.tradecore.legacy.util.ServerNewsFilter;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public final class FeatureSettingsScreen extends BaseScreen {
    private final GuiScreen parent;
    public FeatureSettingsScreen(GuiScreen parent) { this.parent = parent; }

    @Override public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(1, centerX(240), 65, 240, 20, newsText()));
        buttonList.add(new GuiButton(0, centerX(100), 105, 100, 20, "Zurück"));
    }

    private String newsText() { return "GrieferGames Server-News: " + (ConfigManager.get().hideServerNews ? "ausgeblendet" : "sichtbar"); }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0) mc.displayGuiScreen(parent);
        else if (button.id == 1) {
            ConfigManager.get().hideServerNews = !ConfigManager.get().hideServerNews;
            ServerNewsFilter.reset(); ConfigManager.save(); button.displayString = newsText();
        }
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawTradecoreBackground("Funktionen");
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
