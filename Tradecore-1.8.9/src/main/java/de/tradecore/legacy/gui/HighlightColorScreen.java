package de.tradecore.legacy.gui;

import de.tradecore.legacy.config.ConfigManager;
import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

public final class HighlightColorScreen extends BaseScreen {
    private final GuiScreen parent;
    private GuiTextField hex;
    private String error = "";
    public HighlightColorScreen(GuiScreen parent) { this.parent = parent; }

    @Override public void initGui() {
        Keyboard.enableRepeatEvents(true);
        hex = new GuiTextField(1, fontRendererObj, centerX(160), 76, 160, 20);
        hex.setMaxStringLength(7);
        hex.setText(ConfigManager.get().highlightColorHex);
        hex.setFocused(true);
        buttonList.clear();
        buttonList.add(new GuiButton(1, width / 2 - 104, 112, 100, 20, "Speichern"));
        buttonList.add(new GuiButton(0, width / 2 + 4, 112, 100, 20, "Abbrechen"));
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0) mc.displayGuiScreen(parent);
        else if (button.id == 1) {
            String value = hex.getText().trim().toUpperCase();
            if (!value.startsWith("#")) value = "#" + value;
            if (!value.matches("#[0-9A-F]{6}")) error = "Bitte eine Farbe wie #FFAA00 eingeben.";
            else { ConfigManager.get().highlightColorHex = value; ConfigManager.save(); mc.displayGuiScreen(parent); }
        }
    }

    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent); else hex.textboxKeyTyped(typedChar, keyCode);
    }
    @Override protected void mouseClicked(int x, int y, int button) throws IOException { super.mouseClicked(x, y, button); hex.mouseClicked(x, y, button); }
    @Override public void updateScreen() { hex.updateCursorCounter(); }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawTradecoreBackground("Highlight-Farbe");
        drawCenteredString(fontRendererObj, "Hex-Farbwert", width / 2, 58, 0xFFCCCCCC);
        hex.drawTextBox();
        if (!error.isEmpty()) drawCenteredString(fontRendererObj, error, width / 2, 142, 0xFFFF5555);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
