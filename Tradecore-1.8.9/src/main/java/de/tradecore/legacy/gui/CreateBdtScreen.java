package de.tradecore.legacy.gui;

import de.tradecore.legacy.model.ActionResult;
import de.tradecore.legacy.service.BdtService;
import de.tradecore.legacy.util.BdtPreview;
import de.tradecore.legacy.util.Notifications;
import java.io.IOException;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

public final class CreateBdtScreen extends BaseScreen {
    private final GuiScreen parent;
    private GuiTextField reward;
    private String selectedId;
    private String selectedName;
    private String status = "";
    private String rewardDraft = "";

    public CreateBdtScreen(GuiScreen parent) { this.parent = parent; }

    @Override public void initGui() {
        Keyboard.enableRepeatEvents(true);
        reward = new GuiTextField(2, fontRendererObj, centerX(220), 112, 220, 20);
        reward.setMaxStringLength(100);
        reward.setText(rewardDraft);
        reward.setFocused(true);
        buttonList.clear();
        buttonList.add(new GuiButton(1, centerX(220), 76, 220, 20, "Block oder Mob auswählen"));
        buttonList.add(new GuiButton(2, width / 2 - 104, 148, 100, 20, "Vorschlag prüfen"));
        buttonList.add(new GuiButton(0, width / 2 + 4, 148, 100, 20, "Zurück"));
    }

    public void select(String id, String name) {
        selectedId = id;
        selectedName = name;
        status = "Ausgewählt: " + name;
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0) mc.displayGuiScreen(parent);
        else if (button.id == 1) {
            rewardDraft = reward.getText();
            mc.displayGuiScreen(new BlockSelectScreen(this));
        }
        else if (button.id == 2) validateSelection();
    }

    private void validateSelection() {
        final String gain = reward.getText().trim();
        if (selectedId == null || selectedName == null) {
            status = "Bitte wähle einen Block oder ein Mob aus der Liste aus.";
            return;
        }
        if (gain.isEmpty()) {
            status = "Bitte gib den Gewinn ein.";
            return;
        }
        final String id = selectedId;
        final String name = selectedName;
        mc.displayGuiScreen(new ResolveBdtConfirmScreen(CreateBdtScreen.this, id, name, new Runnable() {
            @Override public void run() { submit(id, name, gain); }
        }));
    }

    private void submit(String id, String name, String gain) {
        BdtService.create(id, name, gain, new Consumer<ActionResult>() {
            @Override public void accept(ActionResult result) {
                if (result.success) {
                    Notifications.info(result.message);
                    BdtService.fetch(true, new Runnable() {
                        @Override public void run() { mc.displayGuiScreen(new BdtScreen(parent)); }
                    });
                }
                else { Notifications.error(result.message); mc.displayGuiScreen(CreateBdtScreen.this); status = result.message; }
            }
        });
    }

    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
        else {
            reward.textboxKeyTyped(typedChar, keyCode);
            rewardDraft = reward.getText();
        }
    }
    @Override protected void mouseClicked(int x, int y, int button) throws IOException { super.mouseClicked(x, y, button); reward.mouseClicked(x, y, button); }
    @Override public void updateScreen() { reward.updateCursorCounter(); }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawTradecoreBackground("Neuen BdT vorschlagen");
        drawSelectionPreview();
        drawString(fontRendererObj, "Gewinn", centerX(220), 99, 0xFFCCCCCC);
        reward.drawTextBox();
        drawWrappedCentered(status, 182, 0xFFFFCC55, 320);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawSelectionPreview() {
        if (selectedName == null) {
            drawCenteredString(fontRendererObj, "Noch kein Block oder Mob ausgewählt", width / 2, 55, 0xFFAAAAAA);
            return;
        }
        ItemStack stack = BdtPreview.createStack(selectedId);
        int iconWidth = stack == null ? 0 : 22;
        int rowWidth = iconWidth + fontRendererObj.getStringWidth(selectedName);
        int left = (width - rowWidth) / 2;
        if (stack != null) drawItemIcon(stack, left, 49);
        drawString(fontRendererObj, selectedName, left + iconWidth, 55, 0xFFFFFFFF);
    }
}
