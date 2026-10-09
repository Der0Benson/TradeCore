package Tradecorev2.client.config;

import Tradecorev2.client.Tradecorev2Client;
import Tradecorev2.client.util.HighlightColorUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("TradecoreV2-ConfigManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File configFile;
    private static ModConfig config;

    public static void initialize() {
        configFile = new File(MinecraftClient.getInstance().runDirectory, "config/tradecorev2.json");
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
            try (FileReader reader = new FileReader(configFile)) {
                config = GSON.fromJson(reader, ModConfig.class);
                if (config == null) {
                    LOGGER.warn("Config file was empty, creating new default config.");
                    config = new ModConfig();
                }
            } catch (IOException e) {
                LOGGER.error("Failed to read config file, using default.", e);
                config = new ModConfig();
            }
        } else {
            LOGGER.info("No config file found, creating new default config.");
            config = new ModConfig();
        }

        config.highlightColorHex = HighlightColorUtil.normalizeHex(config.highlightColorHex);

        saveConfig(); // Save to create file if it doesn't exist or add new options
    }

    public static void saveConfig() {
        try {
            File parentDir = configFile.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save config file.", e);
        }
    }
}
