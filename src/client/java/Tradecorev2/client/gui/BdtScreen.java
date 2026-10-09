package Tradecorev2.client.gui;

import Tradecorev2.client.BdtCache;
import Tradecorev2.client.BdtData;
import Tradecorev2.client.BdtFetcher;
import Tradecorev2.client.BdtVoteSubmitter;
import Tradecorev2.client.util.BdtNameUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public class BdtScreen extends Screen {
   private static final int PANEL_WIDTH = 320;
   private static final int PANEL_HEIGHT = 240;
   private Button fastVoteButton;
   private Button slowVoteButton;
   private Button refreshButton;
   private boolean loading;
   private String statusMessage = "";

   public BdtScreen() {
      super(Component.literal("Block des Tages"));
   }

   @Override
   protected void init() {
      super.init();
      int startX = (this.width - PANEL_WIDTH) / 2;
      int startY = (this.height - PANEL_HEIGHT) / 2;
      int buttonY = startY + PANEL_HEIGHT - 30;

      this.refreshButton = this.addRenderableWidget(Button.builder(Component.literal("Aktualisieren"), button -> this.refreshData())
         .bounds(startX + 18, buttonY, 135, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Fertig"), button -> this.onClose())
         .bounds(startX + PANEL_WIDTH - 153, buttonY, 135, 20).build());

      BdtData bdtData = BdtCache.getBdtData();
      if (bdtData != null && !bdtData.has_voted) {
         int voteButtonY = startY + 176;
         this.fastVoteButton = this.addRenderableWidget(Button.builder(Component.literal("Schnell"), button -> this.submitVote("schnell"))
            .bounds(startX + 50, voteButtonY, 105, 20).build());
         this.slowVoteButton = this.addRenderableWidget(Button.builder(Component.literal("Langsam"), button -> this.submitVote("langsam"))
            .bounds(startX + PANEL_WIDTH - 155, voteButtonY, 105, 20).build());
      } else if (bdtData == null) {
         this.addRenderableWidget(Button.builder(Component.literal("BdT erstellen"), button -> {
            if (this.minecraft != null) {
               this.minecraft.gui.setScreen(new CreateBdtScreen());
            }
         }).bounds(startX + 60, startY + 150, 200, 20).build());
      }

      this.setActionsActive(!this.loading);
   }

   private void submitVote(String voteType) {
      BdtData bdtData = BdtCache.getBdtData();
      if (this.loading || bdtData == null || bdtData.has_voted) {
         return;
      }

      this.loading = true;
      this.statusMessage = "Stimme wird gespeichert...";
      this.setActionsActive(false);
      BdtVoteSubmitter.submitBdtVoteAsync(bdtData.bdt_id, voteType, result -> {
         if (!result.success()) {
            this.loading = false;
            this.statusMessage = result.message();
            this.setActionsActive(true);
            return;
         }

         this.statusMessage = "Stimme gespeichert. Aktualisiere Daten...";
         this.refreshData();
      });
   }

   private void refreshData() {
      Minecraft client = Minecraft.getInstance();
      if (client.player == null) {
         this.loading = false;
         this.statusMessage = "BdT-Daten können nur im Spiel geladen werden.";
         this.setActionsActive(true);
         return;
      }

      this.loading = true;
      if (this.statusMessage.isEmpty()) {
         this.statusMessage = "BdT-Daten werden aktualisiert...";
      }
      this.setActionsActive(false);
      BdtFetcher.fetchBdtFromApiAsync(client.player.getUUID().toString(), () -> client.gui.setScreen(new BdtScreen()), true);
   }

   private void setActionsActive(boolean active) {
      if (this.fastVoteButton != null) {
         this.fastVoteButton.active = active;
      }
      if (this.slowVoteButton != null) {
         this.slowVoteButton.active = active;
      }
      if (this.refreshButton != null) {
         this.refreshButton.active = active;
         this.refreshButton.setMessage(Component.literal(active ? "Aktualisieren" : "Wird geladen...")
            .withStyle(active ? ChatFormatting.WHITE : ChatFormatting.YELLOW));
      }
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      int startX = (this.width - PANEL_WIDTH) / 2;
      int startY = (this.height - PANEL_HEIGHT) / 2;
      context.fill(startX, startY, startX + PANEL_WIDTH, startY + PANEL_HEIGHT, -534765536);
      context.outline(startX, startY, PANEL_WIDTH, PANEL_HEIGHT, -1);
      this.drawCentered(context, this.getTitle().copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), startX, startY + 9);

      BdtData bdtData = BdtCache.getBdtData();
      int textY = startY + 34;
      if (bdtData != null) {
         String displayName = BdtNameUtil.getDisplayName(firstNonBlank(bdtData.item_id, bdtData.item_name));
         ItemStack previewStack = BdtNameUtil.createPreviewStack(bdtData.item_id, bdtData.item_name);
         this.drawPreview(context, previewStack, displayName, startX, textY - 4);
         textY += 30;
         this.drawCentered(context, Component.literal("Gewinn: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(safeText(bdtData.gewinn)).withStyle(ChatFormatting.GREEN)), startX, textY);
         textY += 24;

         int totalVotes = Math.max(0, bdtData.schnell_votes) + Math.max(0, bdtData.langsam_votes);
         this.drawCentered(context, Component.literal("Abstimmung · " + totalVotes + " Stimmen").withStyle(ChatFormatting.GOLD), startX, textY);
         textY += 15;
         this.drawCentered(context, voteText("Schnell", bdtData.schnell_votes, totalVotes), startX, textY);
         textY += 14;
         this.drawCentered(context, voteText("Langsam", bdtData.langsam_votes, totalVotes), startX, textY);
         textY += 20;

         Component voteStatus = bdtData.has_voted
            ? Component.literal("Deine Stimme wurde gezählt.").withStyle(ChatFormatting.GREEN)
            : Component.literal("Wie schnell war der BdT erledigt?").withStyle(ChatFormatting.GRAY);
         this.drawCentered(context, voteStatus, startX, textY);
         textY += 15;
         this.drawCentered(context, Component.literal(lastUpdatedText()).withStyle(ChatFormatting.DARK_GRAY), startX, textY);
      } else {
         this.drawCentered(context, Component.literal("Momentan sind keine BdT-Daten geladen.").withStyle(ChatFormatting.RED), startX, textY);
         this.drawCentered(context, Component.literal("Aktualisiere die Daten oder erstelle einen BdT.").withStyle(ChatFormatting.GRAY), startX, textY + 16);
      }

      if (!this.statusMessage.isEmpty()) {
         this.drawCentered(context, Component.literal(this.statusMessage).withStyle(this.loading ? ChatFormatting.YELLOW : ChatFormatting.RED), startX, startY + 158);
      }

      super.extractRenderState(context, mouseX, mouseY, delta);
   }

   private Component voteText(String label, int votes, int totalVotes) {
      int safeVotes = Math.max(0, votes);
      int percentage = totalVotes == 0 ? 0 : Math.round(safeVotes * 100.0F / totalVotes);
      return Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
         .append(Component.literal(safeVotes + " (" + percentage + " %)").withStyle(ChatFormatting.YELLOW));
   }

   private String lastUpdatedText() {
      long updatedAt = BdtCache.getLastUpdatedAt();
      if (updatedAt <= 0L) {
         return "Noch nicht aktualisiert";
      }

      long seconds = Math.max(0L, (System.currentTimeMillis() - updatedAt) / 1000L);
      return seconds < 5L ? "Gerade aktualisiert" : "Vor " + seconds + " Sekunden aktualisiert";
   }

   private static String safeText(String value) {
      return value == null || value.isBlank() ? "Nicht angegeben" : value;
   }

   private static String firstNonBlank(String primary, String fallback) {
      return primary != null && !primary.isBlank() ? primary : fallback;
   }

   private void drawPreview(GuiGraphicsExtractor context, ItemStack stack, String displayName, int panelX, int y) {
      Component name = Component.literal(safeText(displayName)).withStyle(ChatFormatting.AQUA);
      int nameWidth = this.font.width(name);
      int iconAreaWidth = stack.isEmpty() ? 0 : 32;
      int rowWidth = iconAreaWidth + nameWidth;
      int rowX = panelX + (PANEL_WIDTH - rowWidth) / 2;
      if (!stack.isEmpty()) {
         context.fill(rowX, y, rowX + 24, y + 24, 0x66000000);
         context.outline(rowX, y, 24, 24, 0xFF777777);
         context.item(stack, rowX + 4, y + 4);
      }
      context.text(this.font, name, rowX + iconAreaWidth, y + 8, -1);
   }

   private void drawCentered(GuiGraphicsExtractor context, Component text, int panelX, int y) {
      context.text(this.font, text, panelX + (PANEL_WIDTH - this.font.width(text)) / 2, y, -1);
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }
}
