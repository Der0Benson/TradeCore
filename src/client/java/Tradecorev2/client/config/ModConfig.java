package Tradecorev2.client.config;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ModConfig {
   public boolean welcomeShown;
   public String highlightColorHex;
   public int highlightDistance;
   public int highlightRescanSeconds;
   public int highlightParticleAmount;
   public boolean hideServerNews;

   public ModConfig() {
      this.welcomeShown = false;
      this.highlightColorHex = "#FFAA00";
      this.highlightDistance = 32;
      this.highlightRescanSeconds = 10;
      this.highlightParticleAmount = 2;
      this.hideServerNews = false;
   }

}
