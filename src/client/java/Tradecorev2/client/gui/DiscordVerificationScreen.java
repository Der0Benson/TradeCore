package Tradecorev2.client.gui;

import Tradecorev2.client.DiscordLinker;
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
public class DiscordVerificationScreen extends Screen {
   private static final int PANEL_WIDTH = 350;
   private static final int PANEL_HEIGHT = 210;
   private final Screen parent;
   private final String reason;
   private EditBox discordIdField;
   private Button verifyButton;
   private boolean submitting;
   private boolean successful;
   private String statusMessage = "";

   public DiscordVerificationScreen(Screen parent, String reason) {
      super(Component.literal("Discord-Verifizierung"));
      this.parent = parent;
      this.reason = reason == null ? "" : reason;
   }

   @Override
   protected void init() {
      super.init();
      int panelX = (this.width - PANEL_WIDTH) / 2;
      int panelY = (this.height - PANEL_HEIGHT) / 2;
      this.discordIdField = new EditBox(this.font, panelX + 35, panelY + 86, PANEL_WIDTH - 70, 22, Component.literal("Discord-ID"));
      this.discordIdField.setMaxLength(20);
      this.discordIdField.setHint(Component.literal("Discord-ID, z. B. 123456789012345678").withStyle(ChatFormatting.DARK_GRAY));
      this.discordIdField.setResponder(value -> this.updateButtonState());
      this.addRenderableWidget(this.discordIdField);
      this.setInitialFocus(this.discordIdField);

      this.verifyButton = this.addRenderableWidget(Button.builder(Component.literal("Jetzt verknüpfen"), button -> this.submit())
         .bounds(panelX + 35, panelY + 122, 170, 22).build());
      this.addRenderableWidget(Button.builder(Component.literal("Später"), button -> this.onClose())
         .bounds(panelX + PANEL_WIDTH - 125, panelY + 122, 90, 22).build());
      this.updateButtonState();
   }

   private void submit() {
      String discordId = this.discordIdField.getValue().trim();
      if (this.submitting || !discordId.matches("\\d{17,20}")) {
         return;
      }

      this.submitting = true;
      this.statusMessage = "Verknüpfungsanfrage wird gesendet...";
      this.updateButtonState();
      DiscordLinker.sendLinkRequestAsync(discordId, result -> {
         this.submitting = false;
         this.successful = result.success();
         this.statusMessage = result.message();
         this.discordIdField.active = !result.success();
         this.verifyButton.setMessage(Component.literal(result.success() ? "Anfrage gesendet" : "Erneut versuchen"));
         this.updateButtonState();
      });
   }

   private void updateButtonState() {
      if (this.verifyButton != null && this.discordIdField != null) {
         this.verifyButton.active = !this.submitting && !this.successful && this.discordIdField.getValue().trim().matches("\\d{17,20}");
      }
   }

   @Override
   public void onClose() {
      Minecraft client = Minecraft.getInstance();
      if (client != null) {
         client.gui.setScreen(this.parent);
      }
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      int panelX = (this.width - PANEL_WIDTH) / 2;
      int panelY = (this.height - PANEL_HEIGHT) / 2;
      context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, -534765536);
      context.outline(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, -1);
      context.centeredText(this.font, this.title.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), panelX + PANEL_WIDTH / 2, panelY + 10, -1);
      context.centeredText(this.font, Component.literal("Diese Funktion benötigt ein verknüpftes Discord-Konto."), panelX + PANEL_WIDTH / 2, panelY + 34, -1);
      if (!this.reason.isBlank()) {
         context.centeredText(this.font, Component.literal(trimToWidth(this.reason)).withStyle(ChatFormatting.RED), panelX + PANEL_WIDTH / 2, panelY + 50, -1);
      }
      context.text(this.font, Component.literal("Deine Discord-ID:"), panelX + 35, panelY + 72, 0xFFA0A0A0);
      context.centeredText(this.font, Component.literal("Die Bestätigung erhältst du anschließend per Discord-DM.").withStyle(ChatFormatting.GRAY), panelX + PANEL_WIDTH / 2, panelY + 153, -1);
      if (!this.statusMessage.isBlank()) {
         ChatFormatting color = this.successful ? ChatFormatting.GREEN : this.submitting ? ChatFormatting.YELLOW : ChatFormatting.RED;
         context.centeredText(this.font, Component.literal(trimToWidth(this.statusMessage)).withStyle(color), panelX + PANEL_WIDTH / 2, panelY + 174, -1);
      }
      super.extractRenderState(context, mouseX, mouseY, delta);
   }

   private String trimToWidth(String value) {
      return this.font.plainSubstrByWidth(value, PANEL_WIDTH - 30);
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }
}
