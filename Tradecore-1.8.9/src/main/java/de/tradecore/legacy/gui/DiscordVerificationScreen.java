package de.tradecore.legacy.gui;

import de.tradecore.legacy.model.ActionResult;
import de.tradecore.legacy.service.DiscordService;
import de.tradecore.legacy.util.Browser;
import java.io.IOException;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

public final class DiscordVerificationScreen extends BaseScreen {
    private final GuiScreen parent;
    private final String reason;
    private GuiTextField discordId;
    private String status = "";
    private boolean working;

    public DiscordVerificationScreen(GuiScreen parent, String reason) { this.parent = parent; this.reason = reason; }

    @Override public void initGui() {
        Keyboard.enableRepeatEvents(true);
        discordId = new GuiTextField(1, fontRendererObj, centerX(220), 92, 220, 20);
        discordId.setMaxStringLength(20);
        discordId.setFocused(true);
        buttonList.clear();
        buttonList.add(new GuiButton(1, width / 2 - 104, 122, 100, 20, "Verknüpfen"));
        buttonList.add(new GuiButton(2, width / 2 + 4, 122, 100, 20, "Discord öffnen"));
        buttonList.add(new GuiButton(0, centerX(100), 152, 100, 20, "Abbrechen"));
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0) mc.displayGuiScreen(parent);
        else if (button.id == 2) Browser.open(Browser.DISCORD_URL);
        else if (button.id == 1 && !working) {
            working = true; status = "Anfrage wird gesendet ...";
            DiscordService.link(discordId.getText(), new Consumer<ActionResult>() {
                @Override public void accept(ActionResult result) { working = false; status = result.message; }
            });
        }
    }

    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent); else discordId.textboxKeyTyped(typedChar, keyCode);
    }
    @Override protected void mouseClicked(int x, int y, int button) throws IOException { super.mouseClicked(x, y, button); discordId.mouseClicked(x, y, button); }
    @Override public void updateScreen() { discordId.updateCursorCounter(); }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawTradecoreBackground("Discord-Verknüpfung");
        drawWrappedCentered(reason, 52, 0xFFFFCC55, 320);
        drawCenteredString(fontRendererObj, "Discord-Benutzer-ID", width / 2, 78, 0xFFCCCCCC);
        discordId.drawTextBox();
        drawWrappedCentered(status, 180, 0xFFFFFFFF, 300);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
