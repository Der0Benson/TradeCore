package de.tradecore.legacy.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ConfigManager {
    private static final Logger LOGGER = LogManager.getLogger("Tradecore-Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File configFile;
    private static ModConfig config = new ModConfig();

    private ConfigManager() {}

    public static void initialize(File configDirectory) {
        configFile = new File(configDirectory, "tradecore-1.8.9.json");
        if (!configFile.isFile()) {
            save();
            return;
        }
        try (FileReader reader = new FileReader(configFile)) {
            ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
            if (loaded != null) config = loaded;
        } catch (Exception exception) {
            LOGGER.error("Konfiguration konnte nicht geladen werden", exception);
        }
        normalize();
    }

    public static ModConfig get() {
        return config;
    }

    public static File siblingFile(String name) {
        File parent = configFile == null ? new File("config") : configFile.getParentFile();
        return new File(parent, name);
    }

    public static void save() {
        if (configFile == null) return;
        normalize();
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(config, writer);
        } catch (IOException exception) {
            LOGGER.error("Konfiguration konnte nicht gespeichert werden", exception);
        }
    }

    private static void normalize() {
        if (config.highlightColorHex == null || !config.highlightColorHex.matches("#[0-9a-fA-F]{6}")) {
            config.highlightColorHex = "#FFAA00";
        }
        config.highlightDistance = clamp(config.highlightDistance, 8, 128);
        config.highlightRescanSeconds = clamp(config.highlightRescanSeconds, 5, 60);
        config.highlightParticleAmount = clamp(config.highlightParticleAmount, 1, 5);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
