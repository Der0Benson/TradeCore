package de.tradecore.tradecore;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class TradeCoreConfig {

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve(TradeCore.MOD_ID + ".properties");
    // Nur noch die ursprünglichen Keys
    private static final String SHOW_ON_SHIFT_KEY = "showPricesOnlyOnShift";
    private static final String LAST_FETCH_TIMESTAMP_KEY = "lastManualFetchTimestamp";

    // Nur noch die ursprünglichen statischen Variablen
    public static boolean showPricesOnlyOnShift = false;
    public static long lastManualFetchTimestamp = 0L;

    // Lädt die Konfiguration beim Start
    public static void loadConfig() {
        Properties props = new Properties();
        boolean showOnShiftDefault = false;
        long lastFetchDefault = 0L;
        showPricesOnlyOnShift = showOnShiftDefault; // Standardwert setzen
        lastManualFetchTimestamp = lastFetchDefault; // Standardwert setzen

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
                TradeCore.LOGGER.info("Konfiguration geladen: shift={}, lastFetch={}", showPricesOnlyOnShift, lastManualFetchTimestamp);
            } catch (IOException | IllegalArgumentException e) {
                TradeCore.LOGGER.error("Fehler beim Laden der Konfig {}, verwende Defaults.", CONFIG_PATH.getFileName(), e);
                showPricesOnlyOnShift = showOnShiftDefault;
                lastManualFetchTimestamp = lastFetchDefault;
            }
        } else {
            TradeCore.LOGGER.info("Konfig {} nicht gefunden, erstelle Defaults.", CONFIG_PATH.getFileName());
            saveConfigInternal(props); // Speichere Defaults
        }
    }

    // Speichert die aktuelle Konfiguration
    public static synchronized void saveConfig() {
        Properties props = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (var inputStream = Files.newInputStream(CONFIG_PATH)) { props.load(inputStream); }
            catch (IOException e) { TradeCore.LOGGER.error("Konfig lesen vor Speichern fehlgeschlagen: ", e); }
        }
        saveConfigInternal(props);
    }

    // Interne Methode zum Speichern
    private static synchronized void saveConfigInternal(Properties props) {
        props.setProperty(SHOW_ON_SHIFT_KEY, String.valueOf(showPricesOnlyOnShift));
        props.setProperty(LAST_FETCH_TIMESTAMP_KEY, String.valueOf(lastManualFetchTimestamp));
        try (var outputStream = Files.newOutputStream(CONFIG_PATH)) {
            props.store(outputStream, "TradeCore Mod Konfiguration");
        } catch (IOException e) {
            TradeCore.LOGGER.error("Fehler beim Speichern der Konfig {}: ", CONFIG_PATH.getFileName(), e);
        }
    }

    // Methode zum Aktualisieren des Timestamps (bleibt erhalten für Cooldown)
    public static synchronized void updateAndSaveLastFetchTimestamp(long timestamp) {
        lastManualFetchTimestamp = timestamp;
        saveConfig();
        TradeCore.LOGGER.info("Cooldown-Zeitstempel gespeichert: {}", lastManualFetchTimestamp);
    }

    // saveConsentChoice Methode entfernt
}