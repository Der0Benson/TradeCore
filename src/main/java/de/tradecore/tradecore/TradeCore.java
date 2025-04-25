package de.tradecore.tradecore;

import net.fabricmc.api.ModInitializer;
// import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents; // Nicht mehr benötigt
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
// import net.minecraft.client.MinecraftClient; // Nicht mehr benötigt
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeCore implements ModInitializer {
    public static final String MOD_ID = "tradecore";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    // API Client wird wieder direkt initialisiert
    public static PriceAPIClient apiClient;
    // Flags für Consent etc. entfernt
    private static boolean shuttingDown = false;

    @Override
    public void onInitialize() {
        LOGGER.info("TradeCore Mod wird initialisiert...");

        // Lade Konfiguration direkt
        TradeCoreConfig.loadConfig();

        // Initialisiere API-Client direkt
        apiClient = new PriceAPIClient();

        // Lade vorhandene Preise aus lokaler Datei
        apiClient.loadPricesFromDisk();

        // Starte den initialen Preisabruf direkt
        LOGGER.info("Starte initialen Preisabruf...");
        apiClient.fetchAllPricesAsync();

        // Registriere Shutdown-Hooks direkt
        registerShutdownHooks();

        LOGGER.info("TradeCore Initialisierung abgeschlossen.");
        // Kein proceedWithInitialization mehr nötig
        // ClientTooltipHandler registriert sich selbst via Entrypoint
    }


    // Methode zur Registrierung der Hooks (unverändert zur letzten Version)
    private static void registerShutdownHooks() {
        if (shuttingDown) return;
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> cleanupResources("Server Stop", false));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> cleanupResources("Runtime Hook", true), "TradeCore-Shutdown-Hook"));
        LOGGER.info("Shutdown Hooks registriert.");
    }


    // Cleanup-Methode (unverändert zur letzten Version)
    private static synchronized void cleanupResources(String trigger, boolean fullShutdown) {
        LOGGER.info("Starte TradeCore Cleanup (Auslöser: {}, Full Shutdown: {})...", trigger, fullShutdown);
        if (apiClient != null) {
            LOGGER.info("Fahre Executor herunter...");
            apiClient.shutdown(); // Prüft intern
            if (fullShutdown) {
                if (!shuttingDown) {
                    shuttingDown = true;
                    LOGGER.info("Lösche Preisdatei...");
                    apiClient.deletePriceFile();
                    LOGGER.info("TradeCore vollständiger Shutdown abgeschlossen.");
                }
            } else { LOGGER.info("Partieller Cleanup abgeschlossen."); }
        } else {
            LOGGER.warn("TradeCore Cleanup: apiClient war null (Auslöser: {}).", trigger);
            if(fullShutdown && !shuttingDown) shuttingDown = true;
        }
    }
}