package de.tradecore.legacy.gui;

import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;

public abstract class BaseScreen extends GuiScreen {
    protected void drawTradecoreBackground(String title) {
        drawDefaultBackground();
        int panelWidth = Math.min(380, width - 30);
        int left = (width - panelWidth) / 2;
        drawRect(left, 18, left + panelWidth, height - 18, 0xCC101820);
        drawRect(left, 18, left + panelWidth, 20, 0xFFFFAA00);
        drawCenteredString(fontRendererObj, title, width / 2, 29, 0xFFFFAA00);
    }

    protected void drawWrappedCentered(String text, int y, int color, int maxWidth) {
        if (text == null) return;
        List<String> lines = fontRendererObj.listFormattedStringToWidth(text, maxWidth);
        for (String line : lines) {
            drawCenteredString(fontRendererObj, line, width / 2, y, color);
            y += fontRendererObj.FONT_HEIGHT + 2;
        }
    }

    protected int centerX(int elementWidth) { return (width - elementWidth) / 2; }

    protected void drawItemIcon(ItemStack stack, int x, int y) {
        if (stack == null) return;
        GlStateManager.pushMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableDepth();
        GlStateManager.enableRescaleNormal();
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override public boolean doesGuiPauseGame() { return false; }
}
