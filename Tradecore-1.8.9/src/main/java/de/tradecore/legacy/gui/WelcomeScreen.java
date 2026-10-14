package de.tradecore.legacy.gui;

import de.tradecore.legacy.config.ConfigManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public final class WelcomeScreen extends BaseScreen {
    private final GuiScreen parent;
    public WelcomeScreen(GuiScreen parent) { this.parent = parent; }

    @Override public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(1, centerX(140), height - 54, 140, 20, "Verstanden"));
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 1) {
            ConfigManager.get().welcomeShown = true;
            ConfigManager.save();
            mc.displayGuiScreen(parent);
        }
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawTradecoreBackground("Willkommen bei Tradecore");
        drawWrappedCentered("Tradecore bringt den Block des Tages, Abstimmungen, Vorschläge, "
            + "einen Rechner und Block-Highlighting nach Minecraft 1.8.9.", 62, 0xFFFFFFFF, 320);
        drawWrappedCentered("Pfeil hoch: Einstellungen   Pfeil rechts: BdT   "
            + "Pfeil runter: Highlight   Umschalt + Z: Rechner", 116, 0xFFBBBBBB, 320);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
