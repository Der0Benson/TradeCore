package de.tradecore.tradecore;

import net.fabricmc.loader.api.FabricLoader; // Import für den Config-Pfad
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class TradeCoreConfig {

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve(TradeCore.MOD_ID + ".properties");
    private static final String SHOW_ON_SHIFT_KEY = "showPricesOnlyOnShift";

    // Lädt die Konfiguration beim Start
    public static void loadConfig() {
        Properties props = new Properties();
        // Standardwert setzen, falls die Datei nicht existiert oder der Schlüssel fehlt
        boolean showOnShiftDefault = false;
        TradeCore.showPricesOnlyOnShift = showOnShiftDefault; // Setze Standardwert zuerst

        if (Files.exists(CONFIG_PATH)) {
            try (var inputStream = Files.newInputStream(CONFIG_PATH)) {
                props.load(inputStream);
                // Lese den Wert, nutze Standardwert bei Fehler oder Fehlen
                TradeCore.showPricesOnlyOnShift = Boolean.parseBoolean(props.getProperty(SHOW_ON_SHIFT_KEY, String.valueOf(showOnShiftDefault)));
                TradeCore.LOGGER.info("Konfiguration geladen: {} = {}", SHOW_ON_SHIFT_KEY, TradeCore.showPricesOnlyOnShift);
            } catch (IOException | IllegalArgumentException e) {
                TradeCore.LOGGER.error("Fehler beim Laden der Konfigurationsdatei {}, verwende Standardwerte.", CONFIG_PATH, e);
                // Stelle sicher, dass der Standardwert gesetzt ist, falls das Laden fehlschlägt
                TradeCore.showPricesOnlyOnShift = showOnShiftDefault;
                // Optional: Versuche, die Datei mit Standardwerten neu zu schreiben
                saveConfigInternal(props); // Speichert den Standardwert, wenn Laden fehlschlägt
            }
        } else {
            TradeCore.LOGGER.info("Konfigurationsdatei {} nicht gefunden, erstelle mit Standardwerten.", CONFIG_PATH);
            // Speichere die Standardeinstellung, wenn die Datei nicht existiert
            saveConfigInternal(props);
        }
    }

    // Speichert die aktuelle Konfiguration
    public static void saveConfig() {
        Properties props = new Properties();
        saveConfigInternal(props);
    }

    // Interne Methode zum Speichern, um Code-Duplikation zu vermeiden
    private static void saveConfigInternal(Properties props) {
        // Lese vorhandene Properties, falls die Datei existiert, um andere Einstellungen nicht zu überschreiben
        // (Momentan gibt es nur eine, aber das ist gute Praxis für die Zukunft)
        if (Files.exists(CONFIG_PATH)) {
            try (var inputStream = Files.newInputStream(CONFIG_PATH)) {
                props.load(inputStream);
            } catch (IOException e) {
                TradeCore.LOGGER.error("Konnte vorhandene Konfiguration nicht lesen vor dem Speichern: ", e);
                // Fahre trotzdem fort, um die aktuelle Einstellung zu speichern
            }
        }

        props.setProperty(SHOW_ON_SHIFT_KEY, String.valueOf(TradeCore.showPricesOnlyOnShift));
        try (var outputStream = Files.newOutputStream(CONFIG_PATH)) {
            props.store(outputStream, "TradeCore Mod Konfiguration");
            // Kein extra Log hier, das passiert normalerweise nur bei Änderung durch den User
        } catch (IOException e) {
            TradeCore.LOGGER.error("Fehler beim Speichern der Konfigurationsdatei {}: ", CONFIG_PATH, e);
        }
    }
}