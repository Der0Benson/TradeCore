package de.tradecore.tradecore;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class TradeCoreConfig {

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve(TradeCore.MOD_ID + ".properties");
    private static final String SHOW_ON_SHIFT_KEY = "showPricesOnlyOnShift";
    private static final String LAST_FETCH_TIMESTAMP_KEY = "lastManualFetchTimestamp";
    private static final String MOD_ENABLED_KEY = "modEnabled";
    private static final String TUTORIAL_SHOWN_KEY = "tutorialShown";

    public static boolean showPricesOnlyOnShift = false;
    public static long lastManualFetchTimestamp = 0L;
    public static boolean modEnabled = true;
    public static boolean tutorialShown = false;

    public static void loadConfig() {
        Properties props = new Properties();
        boolean showOnShiftDefault = false;
        long lastFetchDefault = 0L;
        boolean modEnabledDefault = true;
        boolean tutorialShownDefault = false;

        // Setze Standardwerte vor dem Laden
        showPricesOnlyOnShift = showOnShiftDefault;
        lastManualFetchTimestamp = lastFetchDefault;
        modEnabled = modEnabledDefault;
        tutorialShown = tutorialShownDefault;

        if (Files.exists(CONFIG_PATH)) {
            try (var inputStream = Files.newInputStream(CONFIG_PATH)) {
                props.load(inputStream);
                showPricesOnlyOnShift = Boolean.parseBoolean(props.getProperty(SHOW_ON_SHIFT_KEY, String.valueOf(showOnShiftDefault)));
                try {
                    lastManualFetchTimestamp = Long.parseLong(props.getProperty(LAST_FETCH_TIMESTAMP_KEY, String.valueOf(lastFetchDefault)));
                } catch (NumberFormatException e) {
                    TradeCore.LOGGER.warn("Ungültiger Wert für '{}' in {}. Verwende 0.", LAST_FETCH_TIMESTAMP_KEY, CONFIG_PATH.getFileName());
                    lastManualFetchTimestamp = lastFetchDefault;
                }
                modEnabled = Boolean.parseBoolean(props.getProperty(MOD_ENABLED_KEY, String.valueOf(modEnabledDefault)));
                tutorialShown = Boolean.parseBoolean(props.getProperty(TUTORIAL_SHOWN_KEY, String.valueOf(tutorialShownDefault)));
                TradeCore.LOGGER.info("Konfiguration geladen: shift={}, lastFetch={}, modEnabled={}, tutorialShown={}", showPricesOnlyOnShift, lastManualFetchTimestamp, modEnabled, tutorialShown);
            } catch (IOException | IllegalArgumentException e) {
                TradeCore.LOGGER.error("Fehler beim Laden der Konfig {}, verwende Defaults.", CONFIG_PATH.getFileName(), e);
                // Rückfall auf Standardwerte, falls Laden fehlschlägt
                showPricesOnlyOnShift = showOnShiftDefault;
                lastManualFetchTimestamp = lastFetchDefault;
                modEnabled = modEnabledDefault;
                tutorialShown = tutorialShownDefault;
            }
        } else {
            TradeCore.LOGGER.info("Konfig {} nicht gefunden, erstelle Defaults.", CONFIG_PATH.getFileName());
            props.setProperty(SHOW_ON_SHIFT_KEY, String.valueOf(showOnShiftDefault));
            props.setProperty(LAST_FETCH_TIMESTAMP_KEY, String.valueOf(lastFetchDefault));
            props.setProperty(MOD_ENABLED_KEY, String.valueOf(modEnabledDefault));
            props.setProperty(TUTORIAL_SHOWN_KEY, String.valueOf(tutorialShownDefault));
            saveConfigInternal(props);
        }
    }

    public static synchronized void saveConfig() {
        Properties props = new Properties();
        // Lade bestehende Properties, um andere Einstellungen nicht zu überschreiben, falls vorhanden
        if (Files.exists(CONFIG_PATH)) {
            try (var inputStream = Files.newInputStream(CONFIG_PATH)) {
                props.load(inputStream);
            } catch (IOException e) {
                TradeCore.LOGGER.error("Konfig lesen vor Speichern fehlgeschlagen: ", e);
            }
        }
        props.setProperty(SHOW_ON_SHIFT_KEY, String.valueOf(showPricesOnlyOnShift));
        props.setProperty(LAST_FETCH_TIMESTAMP_KEY, String.valueOf(lastManualFetchTimestamp));
        props.setProperty(MOD_ENABLED_KEY, String.valueOf(modEnabled));
        props.setProperty(TUTORIAL_SHOWN_KEY, String.valueOf(tutorialShown));
        saveConfigInternal(props);
    }

    private static synchronized void saveConfigInternal(Properties props) {
        try (var outputStream = Files.newOutputStream(CONFIG_PATH)) {
            props.store(outputStream, "TradeCore Mod Konfiguration");
        } catch (IOException e) {
            TradeCore.LOGGER.error("Fehler beim Speichern der Konfig {}: ", CONFIG_PATH.getFileName(), e);
        }
    }

    public static synchronized void updateAndSaveLastFetchTimestamp(long timestamp) {
        lastManualFetchTimestamp = timestamp;
        saveConfig();
        TradeCore.LOGGER.info("Cooldown-Zeitstempel gespeichert: {}", lastManualFetchTimestamp);
    }

    public static synchronized void toggleModEnabled() {
        modEnabled = !modEnabled;
        saveConfig();
        TradeCore.LOGGER.info("Mod-Status geändert auf: {}", modEnabled ? "Aktiviert" : "Deaktiviert");
    }

    public static synchronized void markTutorialAsShown() {
        if (!tutorialShown) {
            tutorialShown = true;
            saveConfig();
            TradeCore.LOGGER.info("Tutorial als angezeigt markiert und Konfiguration gespeichert.");
        }
    }
}