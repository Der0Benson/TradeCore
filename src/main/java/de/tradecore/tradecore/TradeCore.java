package de.tradecore.tradecore;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents; // Import für Shutdown
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeCore implements ModInitializer {
    public static final String MOD_ID = "tradecore";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static PriceAPIClient apiClient;
    public static boolean showPricesOnlyOnShift = false;

    @Override
    public void onInitialize() {
        LOGGER.info("TradeCore Mod wird initialisiert...");

        // Konfiguration laden (unverändert)
        TradeCoreConfig.loadConfig();

        // API-Client initialisieren (unverändert)
        apiClient = new PriceAPIClient();

        // Preise aus lokaler Datei laden (falls vorhanden)
        apiClient.loadPricesFromDisk();

        // Asynchronen Abruf aller Preise von der API starten
        // Dies blockiert den Start nicht
        apiClient.fetchAllPricesAsync();

        // NEU: Registriere einen Shutdown-Hook, um den Executor sauber zu beenden
        // Dies ist eher für Server relevant, aber schadet im Client nicht
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (apiClient != null) {
                LOGGER.info("Shutting down PriceAPIClient executor...");
                apiClient.shutdown();
            }
        });
        // Für den Client könnten wir auch Runtime.getRuntime().addShutdownHook verwenden
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (apiClient != null) {
                LOGGER.info("Shutting down PriceAPIClient executor (Runtime Hook)...");
                apiClient.shutdown();
            }
        }));

    }
}