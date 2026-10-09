package Tradecorev2.client.gui;

import Tradecorev2.client.ActivityStatusChecker;
import Tradecorev2.client.BdtCache;
import Tradecorev2.client.BdtData;
import Tradecorev2.client.BdtFetcher;
import Tradecorev2.client.DiscordOpener;
import Tradecorev2.client.util.ActivityStatusManager;
import Tradecorev2.client.util.BdtNameUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public class SettingsScreen extends Screen {
   private Button activityStatusButton;

   public SettingsScreen() {
      super(Component.literal("TradeCore – BdT-Einstellungen"));
   }

   @Override
   protected void init() {
      super.init();
      int panelWidth = 240;
      int panelHeight = 250;
      int startX = (this.width - panelWidth) / 2;
      int startY = (this.height - panelHeight) / 2;
      int buttonWidth = 210;
      int buttonHeight = 20;
      int spacing = 24;
      int buttonStartY = startY + 62;
      int centerX = startX + (panelWidth - buttonWidth) / 2;
      int row = 0;

      this.addRenderableWidget(Button.builder(Component.literal("BdT öffnen"), button -> this.openBdt(false))
         .bounds(centerX, buttonStartY + spacing * row++, buttonWidth, buttonHeight).build());
      this.addRenderableWidget(Button.builder(Component.literal("BdT-Daten aktualisieren"), button -> {
         button.active = false;
         button.setMessage(Component.literal("BdT wird aktualisiert...").withStyle(ChatFormatting.YELLOW));
         this.refreshBdt(button);
      }).bounds(centerX, buttonStartY + spacing * row++, buttonWidth, buttonHeight).build());
      this.addRenderableWidget(Button.builder(Component.literal("BdT-Highlight konfigurieren"), button -> {
         if (this.minecraft != null) {
            this.minecraft.gui.setScreen(new HighlightSettingsScreen(this));
         }
      }).bounds(centerX, buttonStartY + spacing * row++, buttonWidth, buttonHeight).build());
      this.addRenderableWidget(Button.builder(Component.literal("Discord beitreten"), button -> DiscordOpener.openDiscordInvite())
         .bounds(centerX, buttonStartY + spacing * row++, buttonWidth, buttonHeight).build());

      this.activityStatusButton = Button.builder(this.getActivityStatusText(), button ->
         this.showToast("Aktivitätsstatus", "Der Status kann bis zu einer Stunde benötigen, um sich zu aktualisieren."))
         .bounds(centerX, buttonStartY + spacing * row++, buttonWidth, buttonHeight).build();
      this.addRenderableWidget(this.activityStatusButton);
      this.addRenderableWidget(Button.builder(Component.literal("Fertig"), button -> this.onClose())
         .bounds(startX + (panelWidth - 100) / 2, startY + panelHeight - 28, 100, buttonHeight).build());

      this.requestActivityStatus();
   }

   private void openBdt(boolean forceRefresh) {
      Minecraft client = Minecraft.getInstance();
      if (client.player == null) {
         this.showToast("BdT", "BdT-Daten können nur im Spiel geladen werden.");
         return;
      }

      BdtFetcher.fetchBdtFromApiAsync(
         client.player.getUUID().toString(),
         () -> client.execute(() -> client.gui.setScreen(new BdtScreen())),
         forceRefresh
      );
   }

   private void refreshBdt(Button button) {
      Minecraft client = Minecraft.getInstance();
      if (client.player == null) {
         button.active = true;
         button.setMessage(Component.literal("BdT-Daten aktualisieren"));
         this.showToast("BdT", "BdT-Daten können nur im Spiel geladen werden.");
         return;
      }

      BdtFetcher.fetchBdtFromApiAsync(client.player.getUUID().toString(), () -> {
         button.active = true;
         button.setMessage(Component.literal("BdT-Daten aktualisieren"));
         this.showToast("BdT", "Die BdT-Daten wurden aktualisiert.");
      }, true);
   }

   private Component getActivityStatusText() {
      return Component.literal("Aktivitätsstatus: ").append(ActivityStatusManager.getStatusText());
   }

   private void requestActivityStatus() {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && this.activityStatusButton != null) {
         this.activityStatusButton.setMessage(Component.literal("Aktivitätsstatus: Lade...").withStyle(ChatFormatting.GRAY));
         ActivityStatusChecker.refreshStatusAsync(client.player.getUUID().toString(), () -> {
            if (this.activityStatusButton != null) {
               this.activityStatusButton.setMessage(this.getActivityStatusText());
            }
         });
      }
   }

   private void showToast(String title, String description) {
      Minecraft.getInstance().gui.toastManager().addToast(new SystemToast(
         SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
         Component.literal(title),
         Component.literal(description)
      ));
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      int panelWidth = 240;
      int panelHeight = 250;
      int startX = (this.width - panelWidth) / 2;
      int startY = (this.height - panelHeight) / 2;
      context.fill(startX, startY, startX + panelWidth, startY + panelHeight, -534765536);
      context.outline(startX, startY, panelWidth, panelHeight, -1);

      Component titleText = this.getTitle();
      context.text(this.font, titleText,
         startX + (panelWidth - this.font.width(titleText)) / 2, startY + 8, -1);

      BdtData bdt = BdtCache.getBdtData();
      if (bdt == null) {
         Component status = Component.literal("Aktueller BdT: nicht geladen").withStyle(ChatFormatting.GRAY);
         context.text(this.font, status,
            startX + (panelWidth - this.font.width(status)) / 2, startY + 36, -1);
      } else {
         String displayName = BdtNameUtil.getDisplayName(firstNonBlank(bdt.item_id, bdt.item_name));
         ItemStack previewStack = BdtNameUtil.createPreviewStack(bdt.item_id, bdt.item_name);
         this.drawBdtPreview(context, previewStack, displayName, startX, startY + 29);
      }

      super.extractRenderState(context, mouseX, mouseY, delta);
   }

   private void drawBdtPreview(GuiGraphicsExtractor context, ItemStack stack, String displayName, int panelX, int y) {
      int textWidth = 176;
      String visibleName = this.font.plainSubstrByWidth(displayName == null ? "" : displayName, textWidth);
      Component label = Component.literal("BdT: ").withStyle(ChatFormatting.GRAY)
         .append(Component.literal(visibleName).withStyle(ChatFormatting.AQUA));
      int iconAreaWidth = stack.isEmpty() ? 0 : 26;
      int rowWidth = iconAreaWidth + this.font.width(label);
      int rowX = panelX + (240 - rowWidth) / 2;
      if (!stack.isEmpty()) {
         context.fill(rowX, y, rowX + 22, y + 22, 0x66000000);
         context.outline(rowX, y, 22, 22, 0xFF777777);
         context.item(stack, rowX + 3, y + 3);
      }
      context.text(this.font, label, rowX + iconAreaWidth, y + 7, -1);
   }

   private static String firstNonBlank(String primary, String fallback) {
      return primary != null && !primary.isBlank() ? primary : fallback;
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }
}
