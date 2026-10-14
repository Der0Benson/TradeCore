package de.tradecore.legacy.gui;

import de.tradecore.legacy.util.BdtPreview;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

public final class ResolveBdtConfirmScreen extends BaseScreen {
    private final GuiScreen parent;
    private final String resolvedId;
    private final String resolvedName;
    private final Runnable confirm;

    public ResolveBdtConfirmScreen(GuiScreen parent, String resolvedId, String resolvedName, Runnable confirm) {
        this.parent = parent; this.resolvedId = resolvedId; this.resolvedName = resolvedName; this.confirm = confirm;
    }

    @Override public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(1, width / 2 - 104, 118, 100, 20, "Bestätigen"));
        buttonList.add(new GuiButton(0, width / 2 + 4, 118, 100, 20, "Abbrechen"));
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0) mc.displayGuiScreen(parent);
        else if (button.id == 1 && confirm != null) confirm.run();
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawTradecoreBackground("BdT-Vorschlag prüfen");
        drawPreview();
        drawCenteredString(fontRendererObj, "Diesen Eintrag als heutigen BdT einsenden?", width / 2, 91, 0xFFCCCCCC);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawPreview() {
        ItemStack stack = BdtPreview.createStack(resolvedId);
        int iconWidth = stack == null ? 0 : 22;
        int rowWidth = iconWidth + fontRendererObj.getStringWidth(resolvedName);
        int left = (width - rowWidth) / 2;
        if (stack != null) drawItemIcon(stack, left, 66);
        drawString(fontRendererObj, resolvedName, left + iconWidth, 72, 0xFFFFFFFF);
    }
}
