package Tradecorev2.client.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class ResolveBdtConfirmScreen extends Screen {
   private final Screen parent;
   private final String originalInput;
   private final String resolvedName;
   private final String resolvedId;
   private final Runnable onConfirm;

   public ResolveBdtConfirmScreen(Screen parent, String originalInput, String resolvedName, String resolvedId, Runnable onConfirm) {
      super(Component.literal("BdT bestätigen"));
      this.parent = parent;
      this.originalInput = originalInput;
      this.resolvedName = resolvedName;
      this.resolvedId = resolvedId;
      this.onConfirm = onConfirm;
   }

   protected void init() {
      super.init();
      int panelWidth = 260;
      int panelHeight = 140;
      int startX = (this.width - panelWidth) / 2;
      int startY = (this.height - panelHeight) / 2;
      int buttonWidth = 110;
      int buttonHeight = 20;
      this.addRenderableWidget(Button.builder(Component.literal("Bestätigen"), (button) -> {
         if (this.onConfirm != null) {
            this.onConfirm.run();
         }

         if (Minecraft.getInstance().gui.screen() == this) {
            this.closeToParent();
         }

      }).bounds(startX + 20, startY + panelHeight - 30, buttonWidth, buttonHeight).build());
      this.addRenderableWidget(Button.builder(Component.literal("Abbrechen"), (button) -> this.closeToParent()).bounds(startX + panelWidth - buttonWidth - 20, startY + panelHeight - 30, buttonWidth, buttonHeight).build());
   }

   private void closeToParent() {
      Minecraft client = Minecraft.getInstance();
      if (client != null) {
         client.gui.setScreen(this.parent);
      }

   }

   public void onClose() {
      this.closeToParent();
   }

   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      int panelWidth = 260;
      int panelHeight = 140;
      int startX = (this.width - panelWidth) / 2;
      int startY = (this.height - panelHeight) / 2;
      context.fill(startX, startY, startX + panelWidth, startY + panelHeight, -534765536);
      context.outline(startX, startY, panelWidth, panelHeight, -1);
      int y = startY + 10;
      Component titleText = Component.literal("Ist das dein BdT?");
      int titleWidth = this.font.width(titleText);
      context.text(this.font, titleText, startX + (panelWidth - titleWidth) / 2, y, -1);
      y += 18;
      context.text(this.font, Component.literal("Deine Eingabe: ").withStyle(ChatFormatting.GRAY).append(Component.literal(this.originalInput).withStyle(ChatFormatting.WHITE)), startX + 14, y, -1);
      y += 14;
      context.text(this.font, Component.literal("API-Vorschlag: ").withStyle(ChatFormatting.GRAY).append(Component.literal(this.resolvedName).withStyle(ChatFormatting.GOLD)), startX + 14, y, -1);
      y += 14;
      if (this.resolvedId != null && !this.resolvedId.isBlank()) {
         context.text(this.font, Component.literal("Eintrag-ID: ").withStyle(ChatFormatting.GRAY).append(Component.literal(this.resolvedId).withStyle(ChatFormatting.AQUA)), startX + 14, y, -1);
      }

      super.extractRenderState(context, mouseX, mouseY, delta);
   }

   public boolean isPauseScreen() {
      return false;
   }
}
