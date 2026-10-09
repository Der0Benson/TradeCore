package Tradecorev2.client.config;

import Tradecorev2.client.util.HighlightColorUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class ConfigManager {
   private static final Logger LOGGER = LoggerFactory.getLogger("TradecoreV2-ConfigManager");
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   private static File configFile;
   private static ModConfig config;

   public static void initialize() {
      configFile = new File(Minecraft.getInstance().gameDirectory, "config/tradecorev2.json");
      loadConfig();
   }

   public static ModConfig getConfig() {
      if (config == null) {
         loadConfig();
      }

      return config;
   }

   public static void loadConfig() {
      if (configFile.exists()) {
         try {
            FileReader reader = new FileReader(configFile);

            try {
               config = (ModConfig)GSON.fromJson(reader, ModConfig.class);
               if (config == null) {
                  LOGGER.warn("Config file was empty, creating new default config.");
                  config = new ModConfig();
               }
            } catch (Throwable var4) {
               try {
                  reader.close();
               } catch (Throwable var3) {
                  var4.addSuppressed(var3);
               }

               throw var4;
            }

            reader.close();
         } catch (IOException e) {
            LOGGER.error("Failed to read config file, using default.", e);
            config = new ModConfig();
         }
      } else {
         LOGGER.info("No config file found, creating new default config.");
         config = new ModConfig();
      }

      config.highlightColorHex = HighlightColorUtil.normalizeHex(config.highlightColorHex);
      config.highlightDistance = clamp(config.highlightDistance, 8, 128);
      config.highlightRescanSeconds = clamp(config.highlightRescanSeconds, 5, 60);
      config.highlightParticleAmount = clamp(config.highlightParticleAmount, 1, 5);
      saveConfig();
   }

   public static void saveConfig() {
      try {
         File parentDir = configFile.getParentFile();
         if (!parentDir.exists()) {
            parentDir.mkdirs();
         }

         FileWriter writer = new FileWriter(configFile);

         try {
            GSON.toJson(config, writer);
         } catch (Throwable var5) {
            try {
               writer.close();
            } catch (Throwable var4) {
               var5.addSuppressed(var4);
            }

            throw var5;
         }

         writer.close();
      } catch (IOException e) {
         LOGGER.error("Failed to save config file.", e);
      }

   }

   private static int clamp(int value, int min, int max) {
      if (value < min) {
         return min;
      } else {
         return value > max ? max : value;
      }
   }
}
