package de.tradecore.legacy.gui;

import de.tradecore.legacy.util.Calculator;
import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

public final class CalculatorScreen extends BaseScreen {
    private final GuiScreen parent;
    private GuiTextField input;
    private String result = "";
    public CalculatorScreen() { this(null); }
    public CalculatorScreen(GuiScreen parent) { this.parent = parent; }

    @Override public void initGui() {
        Keyboard.enableRepeatEvents(true);
        input = new GuiTextField(1, fontRendererObj, centerX(220), 68, 220, 20);
        input.setMaxStringLength(80);
        input.setFocused(true);
        buttonList.clear();
        buttonList.add(new GuiButton(1, width / 2 - 104, 100, 100, 20, "Berechnen"));
        buttonList.add(new GuiButton(2, width / 2 + 4, 100, 100, 20, "Leeren"));
        buttonList.add(new GuiButton(0, width / 2 - 50, 144, 100, 20, "Zurück"));
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0) mc.displayGuiScreen(parent);
        else if (button.id == 1) calculate();
        else if (button.id == 2) { input.setText(""); result = ""; }
    }

    private void calculate() {
        String value = Calculator.evaluate(input.getText());
        result = value == null ? "Ungültige Rechnung" : value;
    }

    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(parent); return; }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) calculate();
        else input.textboxKeyTyped(typedChar, keyCode);
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        input.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override public void updateScreen() { input.updateCursorCounter(); }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawTradecoreBackground("Tradecore Rechner");
        drawCenteredString(fontRendererObj, "Beispiel: 100/5, 6*7 oder 5+3", width / 2, 50, 0xFFAAAAAA);
        input.drawTextBox();
        drawCenteredString(fontRendererObj, result, width / 2, 128, result.startsWith("=") ? 0xFF55FF55 : 0xFFFF5555);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
