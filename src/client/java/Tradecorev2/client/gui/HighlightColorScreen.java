package Tradecorev2.client.gui;

import Tradecorev2.client.config.ConfigManager;
import Tradecorev2.client.config.ModConfig;
import Tradecorev2.client.util.HighlightColorUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class HighlightColorScreen extends Screen {
   private static final int PANEL_WIDTH = 240;
   private static final int PANEL_HEIGHT = 220;
   private final Screen parent;
   private EditBox hexField;
   private String errorMessage;
   private int panelX;
   private int panelY;

   public HighlightColorScreen(Screen parent) {
      super(Component.literal("Highlight-Farbe"));
      this.parent = parent;
   }

   protected void init() {
      super.init();
      ModConfig config = ConfigManager.getConfig();
      this.panelX = (this.width - 240) / 2;
      this.panelY = (this.height - 220) / 2;
      int centerX = this.panelX + 120;
      this.hexField = new EditBox(this.font, centerX - 80, this.panelY + 64, 160, 20, Component.literal("Hex-Farbe"));
      this.hexField.setValue(config.highlightColorHex);
      this.hexField.setResponder((value) -> this.errorMessage = null);
      this.hexField.setMaxLength(7);
      this.addRenderableWidget(this.hexField);
      this.setInitialFocus(this.hexField);
      this.addRenderableWidget(Button.builder(Component.literal("Speichern"), (button) -> {
         if (HighlightColorUtil.isValidHex(this.hexField.getValue())) {
            config.highlightColorHex = HighlightColorUtil.normalizeHex(this.hexField.getValue());
            ConfigManager.saveConfig();
            this.minecraft.gui.setScreen(this.parent);
         } else {
            this.errorMessage = "Bitte einen Hexwert im Format #RRGGBB eingeben.";
         }

      }).bounds(centerX - 102, this.panelY + 168, 90, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Abbrechen"), (button) -> this.minecraft.gui.setScreen(this.parent)).bounds(centerX + 12, this.panelY + 168, 90, 20).build());
   }

   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      context.fill(this.panelX, this.panelY, this.panelX + 240, this.panelY + 220, -534765536);
      context.outline(this.panelX, this.panelY, 240, 220, -1);
      super.extractRenderState(context, mouseX, mouseY, delta);
      int centerX = this.panelX + 120;
      context.centeredText(this.font, this.title, centerX, this.panelY + 16, -1);
      context.centeredText(this.font, Component.literal("Hex-Farbe (z.B. #FFAA00)").withStyle(ChatFormatting.GRAY), centerX, this.panelY + 40, -1);
      this.drawPreview(context, this.hexField.getValue(), this.panelY + 110);
      if (this.errorMessage != null) {
         context.centeredText(this.font, Component.literal(this.errorMessage).withStyle(ChatFormatting.RED), centerX, this.panelY + 150, -1);
      }

   }

   private void drawPreview(GuiGraphicsExtractor context, String input, int y) {
      String normalized = HighlightColorUtil.normalizeHex(input);
      int rgb = Integer.parseInt(normalized.substring(1), 16);
      int argb = -16777216 | rgb;
      int previewWidth = 120;
      int previewHeight = 24;
      int previewX = this.panelX + (240 - previewWidth) / 2;
      context.centeredText(this.font, Component.literal("Vorschau:"), this.panelX + 120, y - 14, -1);
      context.fill(previewX, y, previewX + previewWidth, y + previewHeight, argb);
      context.outline(previewX, y, previewWidth, previewHeight, -1);
      context.centeredText(this.font, Component.literal(normalized), this.panelX + 120, y + previewHeight + 6, -1);
   }

   public void onClose() {
      Minecraft.getInstance().gui.setScreen(this.parent);
   }

   public boolean isPauseScreen() {
      return false;
   }
}
