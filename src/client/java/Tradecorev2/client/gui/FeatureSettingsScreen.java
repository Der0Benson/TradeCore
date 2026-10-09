package Tradecorev2.client.gui;

import Tradecorev2.client.config.ConfigManager;
import Tradecorev2.client.config.ModConfig;
import Tradecorev2.client.util.ServerNewsFilter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class FeatureSettingsScreen extends Screen {
   private static final int PANEL_WIDTH = 280;
   private static final int PANEL_HEIGHT = 150;
   private final Screen parent;
   private Button hideServerNewsButton;

   public FeatureSettingsScreen(Screen parent) {
      super(Component.literal("TradeCore – Funktionen"));
      this.parent = parent;
   }

   @Override
   protected void init() {
      super.init();
      int startX = (this.width - PANEL_WIDTH) / 2;
      int startY = (this.height - PANEL_HEIGHT) / 2;
      int buttonWidth = 240;
      int buttonX = startX + (PANEL_WIDTH - buttonWidth) / 2;

      this.hideServerNewsButton = Button.builder(this.getHideServerNewsText(), button -> this.toggleServerNews())
         .bounds(buttonX, startY + 58, buttonWidth, 20)
         .build();
      this.addRenderableWidget(this.hideServerNewsButton);
      this.addRenderableWidget(Button.builder(Component.literal("Zurück"), button -> this.onClose())
         .bounds(startX + (PANEL_WIDTH - 100) / 2, startY + PANEL_HEIGHT - 30, 100, 20)
         .build());
   }

   private void toggleServerNews() {
      ModConfig config = ConfigManager.getConfig();
      config.hideServerNews = !config.hideServerNews;
      ServerNewsFilter.reset();
      ConfigManager.saveConfig();
      this.hideServerNewsButton.setMessage(this.getHideServerNewsText());
   }

   private Component getHideServerNewsText() {
      boolean enabled = ConfigManager.getConfig().hideServerNews;
      return Component.literal("Server-News ausblenden: ")
         .append(Component.literal(enabled ? "AN" : "AUS").withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
   }

   @Override
   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(this.parent);
      }
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      int startX = (this.width - PANEL_WIDTH) / 2;
      int startY = (this.height - PANEL_HEIGHT) / 2;
      context.fill(startX, startY, startX + PANEL_WIDTH, startY + PANEL_HEIGHT, -534765536);
      context.outline(startX, startY, PANEL_WIDTH, PANEL_HEIGHT, -1);

      Component titleText = this.getTitle();
      context.text(this.font, titleText, startX + (PANEL_WIDTH - this.font.width(titleText)) / 2, startY + 10, -1);

      super.extractRenderState(context, mouseX, mouseY, delta);
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }
}
