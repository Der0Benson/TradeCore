package de.tradecore.legacy.gui;

import de.tradecore.legacy.model.ActionResult;
import de.tradecore.legacy.model.BdtData;
import de.tradecore.legacy.service.BdtService;
import de.tradecore.legacy.util.BdtPreview;
import de.tradecore.legacy.util.Notifications;
import java.util.Collections;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

public final class BdtScreen extends BaseScreen {
    private final GuiScreen parent;
    private GuiButton reloadButton;
    private boolean loading;

    public BdtScreen() { this(null); }
    public BdtScreen(GuiScreen parent) { this.parent = parent; }

    @Override public void initGui() {
        buttonList.clear();
        int y = height - 58;
        BdtData data = BdtService.getCurrent();
        if (data != null) {
            int left = width / 2 - 118;
            GuiButton fast = new GuiButton(1, left, y, 100, 20, "Schnell");
            GuiButton slow = new GuiButton(2, left + 108, y, 100, 20, "Langsam");
            fast.enabled = slow.enabled = !data.has_voted;
            buttonList.add(fast);
            buttonList.add(slow);
            reloadButton = new GuiButton(4, left + 216, y, 20, 20, loading ? "..." : "↻");
        } else {
            int left = width / 2 - 104;
            buttonList.add(new GuiButton(3, left, y, 180, 20, "BdT vorschlagen"));
            reloadButton = new GuiButton(4, left + 184, y, 20, 20, loading ? "..." : "↻");
        }
        reloadButton.enabled = !loading;
        buttonList.add(reloadButton);
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 1) vote("schnell");
        else if (button.id == 2) vote("langsam");
        else if (button.id == 3) mc.displayGuiScreen(new CreateBdtScreen(this));
        else if (button.id == 4) reload();
    }

    private void reload() {
        loading = true;
        initGui();
        BdtService.fetch(true, new Runnable() {
            @Override public void run() {
                loading = false;
                initGui();
            }
        });
    }

    private void vote(String type) {
        BdtService.vote(type, new Consumer<ActionResult>() {
            @Override public void accept(ActionResult result) {
                if (result.success) BdtService.markVoted();
                if (result.success) Notifications.info(result.message); else Notifications.error(result.message);
                initGui();
            }
        });
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawTradecoreBackground("Block des Tages");
        BdtData data = BdtService.getCurrent();
        if (data == null) {
            drawWrappedCentered("Für heute ist noch kein Block des Tages vorhanden.", 65, 0xFFDDDDDD, 320);
        } else {
            drawBdtPreview(data);
            drawCenteredString(fontRendererObj, "Gewinn: " + safe(data.gewinn, "-"), width / 2, 89, 0xFFFFAA00);
            int total = data.schnell_votes + data.langsam_votes;
            int fastPercent = total == 0 ? 0 : data.schnell_votes * 100 / total;
            int slowPercent = total == 0 ? 0 : 100 - fastPercent;
            drawCenteredString(fontRendererObj, "Schnell: " + data.schnell_votes + " (" + fastPercent + "%)", width / 2, 113, 0xFF55FF55);
            drawCenteredString(fontRendererObj, "Langsam: " + data.langsam_votes + " (" + slowPercent + "%)", width / 2, 127, 0xFFFF5555);
            if (data.has_voted) drawCenteredString(fontRendererObj, "Du hast bereits abgestimmt.", width / 2, 147, 0xFFAAAAAA);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (reloadButton != null && !loading
            && mouseX >= reloadButton.xPosition && mouseX < reloadButton.xPosition + reloadButton.width
            && mouseY >= reloadButton.yPosition && mouseY < reloadButton.yPosition + reloadButton.height) {
            drawHoveringText(Collections.singletonList("Neu laden"), mouseX, mouseY);
        }
    }

    private void drawBdtPreview(BdtData data) {
        String name = safe(data.item_name, data.item_id);
        ItemStack stack = BdtPreview.createStack(data.item_id);
        int iconWidth = stack == null ? 0 : 28;
        int rowWidth = iconWidth + fontRendererObj.getStringWidth(name);
        int left = (width - rowWidth) / 2;
        if (stack != null) {
            drawRect(left, 53, left + 22, 75, 0x66000000);
            drawRect(left, 53, left + 22, 54, 0xFF777777);
            drawRect(left, 74, left + 22, 75, 0xFF777777);
            drawRect(left, 53, left + 1, 75, 0xFF777777);
            drawRect(left + 21, 53, left + 22, 75, 0xFF777777);
            drawItemIcon(stack, left + 3, 56);
        }
        drawString(fontRendererObj, name, left + iconWidth, 60, 0xFF55FFFF);
    }

    private static String safe(String first, String fallback) { return first == null || first.trim().isEmpty() ? fallback : first; }
}
