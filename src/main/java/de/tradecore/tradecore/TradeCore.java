package de.tradecore.tradecore;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeCore implements ModInitializer {
    public static final String MOD_ID = "tradecore";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static PriceAPIClient apiClient;

    // Einstellung wird jetzt von TradeCoreConfig verwaltet
    public static boolean showPricesOnlyOnShift = false; // Wird von loadConfig überschrieben

    @Override
    public void onInitialize() {
        LOGGER.info("TradeCore Mod wird initialisiert...");

        // NEU: Konfiguration laden
        TradeCoreConfig.loadConfig(); // Lädt den Wert aus der Datei oder setzt Standardwert

        // API-Client initialisieren
        apiClient = new PriceAPIClient();
    }
}