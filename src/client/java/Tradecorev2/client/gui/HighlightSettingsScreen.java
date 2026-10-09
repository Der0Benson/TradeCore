package Tradecorev2.client.gui;

import Tradecorev2.client.config.ConfigManager;
import Tradecorev2.client.config.ModConfig;
import Tradecorev2.client.util.HighlightColorUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class HighlightSettingsScreen extends Screen {
   private static final int PANEL_WIDTH = 260;
   private static final int PANEL_HEIGHT = 240;
   private static final int SLIDER_WIDTH = 200;
   private static final int SLIDER_HEIGHT = 20;
   private static final int MIN_DISTANCE = 8;
   private static final int MAX_DISTANCE = 128;
   private static final int MIN_RESCAN_SECONDS = 5;
   private static final int MAX_RESCAN_SECONDS = 60;
   private static final int MIN_PARTICLE_AMOUNT = 1;
   private static final int MAX_PARTICLE_AMOUNT = 5;
   private final Screen parent;
   private final ModConfig config;
   private int panelX;
   private int panelY;

   public HighlightSettingsScreen(Screen parent) {
      super(Component.literal("BdT-Highlight"));
      this.parent = parent;
      this.config = ConfigManager.getConfig();
   }

   protected void init() {
      super.init();
      this.panelX = (this.width - 260) / 2;
      this.panelY = (this.height - 240) / 2;
      int centerX = this.panelX + 130;
      int sliderX = centerX - 100;
      int startY = this.panelY + 36;
      int spacing = 26;
      AbstractSliderButton colorSlider = new AbstractSliderButton(sliderX, startY, 200, 20, Component.literal(""), this.getHueValueFromConfig()) {
         protected void updateMessage() {
            String hex = HighlightSettingsScreen.formatHex(HighlightSettingsScreen.hueToRgb(this.value * (double)360.0F));
            this.setMessage(Component.literal("Highlight-Farbe: " + hex));
         }

         protected void applyValue() {
            int rgb = HighlightSettingsScreen.hueToRgb(this.value * (double)360.0F);
            HighlightSettingsScreen.this.config.highlightColorHex = HighlightColorUtil.normalizeHex(HighlightSettingsScreen.formatHex(rgb));
            ConfigManager.saveConfig();
         }
      };
      colorSlider.setMessage(Component.literal("Highlight-Farbe: " + this.config.highlightColorHex));
      this.addRenderableWidget(colorSlider);
      AbstractSliderButton distanceSlider = new AbstractSliderButton(sliderX, startY + spacing, 200, 20, Component.literal(""), toSliderValue(this.config.highlightDistance, 8, 128)) {
         protected void updateMessage() {
            int distance = HighlightSettingsScreen.toValue(this.value, 8, 128);
            this.setMessage(Component.literal("Distanz: " + distance + " Blöcke"));
         }

         protected void applyValue() {
            HighlightSettingsScreen.this.config.highlightDistance = HighlightSettingsScreen.toValue(this.value, 8, 128);
            ConfigManager.saveConfig();
         }
      };
      distanceSlider.setMessage(Component.literal("Distanz: " + this.config.highlightDistance + " Blöcke"));
      this.addRenderableWidget(distanceSlider);
      AbstractSliderButton rescanSlider = new AbstractSliderButton(sliderX, startY + spacing * 2, 200, 20, Component.literal(""), toSliderValue(this.config.highlightRescanSeconds, 5, 60)) {
         protected void updateMessage() {
            int seconds = HighlightSettingsScreen.toValue(this.value, 5, 60);
            this.setMessage(Component.literal("Aktualisierung: " + seconds + "s"));
         }

         protected void applyValue() {
            HighlightSettingsScreen.this.config.highlightRescanSeconds = HighlightSettingsScreen.toValue(this.value, 5, 60);
            ConfigManager.saveConfig();
         }
      };
      rescanSlider.setMessage(Component.literal("Aktualisierung: " + this.config.highlightRescanSeconds + "s"));
      this.addRenderableWidget(rescanSlider);
      AbstractSliderButton particleSlider = new AbstractSliderButton(sliderX, startY + spacing * 3, 200, 20, Component.literal(""), toSliderValue(this.config.highlightParticleAmount, 1, 5)) {
         protected void updateMessage() {
            int amount = HighlightSettingsScreen.toValue(this.value, 1, 5);
            this.setMessage(Component.literal("Partikel: " + amount + "x"));
         }

         protected void applyValue() {
            HighlightSettingsScreen.this.config.highlightParticleAmount = HighlightSettingsScreen.toValue(this.value, 1, 5);
            ConfigManager.saveConfig();
         }
      };
      particleSlider.setMessage(Component.literal("Partikel: " + this.config.highlightParticleAmount + "x"));
      this.addRenderableWidget(particleSlider);
      this.addRenderableWidget(Button.builder(Component.literal("Fertig"), (button) -> this.onClose()).bounds(centerX - 50, this.panelY + 240 - 28, 100, 20).build());
   }

   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      context.fill(this.panelX, this.panelY, this.panelX + 260, this.panelY + 240, -534765536);
      context.outline(this.panelX, this.panelY, 260, 240, -1);
      int titleX = this.panelX + (260 - this.font.width(this.title)) / 2;
      context.text(this.font, this.title, titleX, this.panelY + 10, -1);
      int previewRgb = parseHexColor(this.config.highlightColorHex);
      int previewWidth = 120;
      int previewHeight = 14;
      int previewX = this.panelX + (260 - previewWidth) / 2;
      int previewY = this.panelY + 154;
      context.centeredText(this.font, Component.literal("Vorschau"), this.panelX + 130, previewY - 12, -1);
      context.fill(previewX, previewY, previewX + previewWidth, previewY + previewHeight, -16777216 | previewRgb);
      context.outline(previewX, previewY, previewWidth, previewHeight, -1);
      super.extractRenderState(context, mouseX, mouseY, delta);
   }

   public void onClose() {
      Minecraft.getInstance().gui.setScreen(this.parent);
   }

   public boolean isPauseScreen() {
      return false;
   }

   private double getHueValueFromConfig() {
      int rgb = parseHexColor(this.config.highlightColorHex);
      int r = rgb >> 16 & 255;
      int g = rgb >> 8 & 255;
      int b = rgb & 255;
      double hue = rgbToHue((double)r / (double)255.0F, (double)g / (double)255.0F, (double)b / (double)255.0F);
      return hue / (double)360.0F;
   }

   private static double rgbToHue(double r, double g, double b) {
      double max = Math.max(r, Math.max(g, b));
      double min = Math.min(r, Math.min(g, b));
      double delta = max - min;
      if (delta == (double)0.0F) {
         return (double)0.0F;
      } else {
         double hue;
         if (max == r) {
            hue = (g - b) / delta % (double)6.0F;
         } else if (max == g) {
            hue = (b - r) / delta + (double)2.0F;
         } else {
            hue = (r - g) / delta + (double)4.0F;
         }

         hue *= (double)60.0F;
         if (hue < (double)0.0F) {
            hue += (double)360.0F;
         }

         return hue;
      }
   }

   private static int hueToRgb(double hue) {
      double h = hue % (double)360.0F;
      double c = (double)1.0F;
      double x = c * ((double)1.0F - Math.abs(h / (double)60.0F % (double)2.0F - (double)1.0F));
      double r = (double)0.0F;
      double g = (double)0.0F;
      double b = (double)0.0F;
      if (h < (double)60.0F) {
         r = c;
         g = x;
      } else if (h < (double)120.0F) {
         r = x;
         g = c;
      } else if (h < (double)180.0F) {
         g = c;
         b = x;
      } else if (h < (double)240.0F) {
         g = x;
         b = c;
      } else if (h < (double)300.0F) {
         r = x;
         b = c;
      } else {
         r = c;
         b = x;
      }

      int ri = (int)Math.round(r * (double)255.0F);
      int gi = (int)Math.round(g * (double)255.0F);
      int bi = (int)Math.round(b * (double)255.0F);
      return ri << 16 | gi << 8 | bi;
   }

   private static int parseHexColor(String hex) {
      String normalized = HighlightColorUtil.normalizeHex(hex);
      return Integer.parseInt(normalized.substring(1), 16);
   }

   private static String formatHex(int rgb) {
      return String.format("#%06X", rgb);
   }

   private static int toValue(double value, int min, int max) {
      if (max <= min) {
         return min;
      } else {
         int result = (int)Math.round((double)min + value * (double)(max - min));
         if (result < min) {
            return min;
         } else {
            return result > max ? max : result;
         }
      }
   }

   private static double toSliderValue(int value, int min, int max) {
      if (max <= min) {
         return (double)0.0F;
      } else if (value < min) {
         return (double)0.0F;
      } else {
         return value > max ? (double)1.0F : (double)(value - min) / (double)(max - min);
      }
   }
}
