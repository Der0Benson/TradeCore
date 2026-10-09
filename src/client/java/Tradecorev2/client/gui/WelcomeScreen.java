package Tradecorev2.client.gui;

import Tradecorev2.client.config.ConfigManager;
import Tradecorev2.client.config.ModConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
public class WelcomeScreen extends Screen {
   private static final int PANEL_WIDTH = 380;
   private static final int PANEL_HEIGHT = 250;
   private static final int PANEL_PADDING = 18;
   private static final int HEADER_HEIGHT = 28;
   private static final int CONTENT_INSET = 8;
   private static final int CARD_PADDING = 6;
   private static final int CARD_GAP = 8;
   private static final int CARD_ACCENT_WIDTH = 2;
   private static final int COLOR_SHADOW = Integer.MIN_VALUE;
   private static final int COLOR_PANEL = -535291880;
   private static final int COLOR_PANEL_TOP = -534502364;
   private static final int COLOR_HEADER = -5146592;
   private static final int COLOR_HEADER_DARK = -9815540;
   private static final int COLOR_CARD = 1612718112;
   private static final int COLOR_CARD_BORDER = 822083583;
   private static final int COLOR_CARD_ACCENT = -5146592;
   private static final ItemStack ICON_STACK;
   private final Screen parent;
   private final List<List<FormattedCharSequence>> paragraphs = new ArrayList();

   public WelcomeScreen(Screen parent) {
      super(Component.literal("Willkommen bei TradeCore"));
      this.parent = parent;
   }

   protected void init() {
      super.init();
      int panelX = (this.width - 380) / 2;
      int panelY = (this.height - 250) / 2;
      int buttonY = panelY + 250 - 32;
      Button continueButton = Button.builder(Component.literal("Verstanden"), (button) -> this.onClose()).bounds(panelX + 130, buttonY, 120, 20).build();
      this.addRenderableWidget(continueButton);
      int cardWidth = 344;
      int textWidth = cardWidth - 8 - 6;
      this.paragraphs.clear();
      this.paragraphs.add(this.font.split(Component.literal("TradeCore bringt den Block des Tages (BdT) direkt ins Spiel. Du kannst den aktuellen BdT ansehen, über seine Laufzeit abstimmen und neue Vorschläge erstellen."), textWidth));
      this.paragraphs.add(this.font.split(Component.literal("Mit dem BdT-Highlight findest du den gesuchten Block in deiner Umgebung. Farbe, Reichweite und Aktualisierung lassen sich in den Einstellungen anpassen."), textWidth));
      this.paragraphs.add(this.font.split(Component.literal("Öffne den BdT mit der rechten Pfeiltaste und schalte das Highlight mit der Pfeiltaste nach unten ein oder aus."), textWidth));
   }

   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      int panelX = (this.width - 380) / 2;
      int panelY = (this.height - 250) / 2;
      context.fill(panelX + 2, panelY + 2, panelX + 380 + 2, panelY + 250 + 2, Integer.MIN_VALUE);
      context.fill(panelX, panelY, panelX + 380, panelY + 250, -535291880);
      context.fill(panelX, panelY, panelX + 380, panelY + 28 + 6, -534502364);
      context.outline(panelX, panelY, 380, 250, -1);
      context.fill(panelX, panelY, panelX + 380, panelY + 28, -5146592);
      context.fill(panelX, panelY + 28 - 1, panelX + 380, panelY + 28, -9815540);
      int iconX = panelX + 10;
      int iconY = panelY + 6;
      context.item(ICON_STACK, iconX, iconY);
      Component titleText = this.getTitle();
      Objects.requireNonNull(this.font);
      int titleY = panelY + (28 - 9) / 2;
      context.text(this.font, titleText, iconX + 22, titleY, -1);
      Objects.requireNonNull(this.font);
      int lineHeight = 9 + 2;
      int cardX = panelX + 18;
      int cardWidth = 344;
      int textX = cardX + 8;
      int textY = panelY + 28 + 12;

      for(int i = 0; i < this.paragraphs.size(); ++i) {
         List<FormattedCharSequence> lines = (List)this.paragraphs.get(i);
         int linesHeight = lines.size() * lineHeight;
         int cardY = textY - 6;
         int cardHeight = linesHeight + 12;
         context.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, 1612718112);
         context.outline(cardX, cardY, cardWidth, cardHeight, 822083583);
         context.fill(cardX, cardY, cardX + 2, cardY + cardHeight, -5146592);

         for(FormattedCharSequence line : lines) {
            context.text(this.font, line, textX, textY, -1);
            textY += lineHeight;
         }

         textY = cardY + cardHeight + 8;
      }

      super.extractRenderState(context, mouseX, mouseY, delta);
   }

   public void onClose() {
      this.markWelcomeShown();
      Minecraft client = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
      if (client != null) {
         client.gui.setScreen(this.parent);
      }

   }

   public boolean isPauseScreen() {
      return false;
   }

   private void markWelcomeShown() {
      ModConfig config = ConfigManager.getConfig();
      if (!config.welcomeShown) {
         config.welcomeShown = true;
         ConfigManager.saveConfig();
      }

   }

   static {
      ICON_STACK = new ItemStack(Items.BOOK);
   }
}
