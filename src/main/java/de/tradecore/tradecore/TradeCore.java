package de.tradecore.tradecore;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeCore implements ModInitializer {
    public static final String MOD_ID = "tradecore";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static PriceAPIClient apiClient;

    @Override
    public void onInitialize() {
        LOGGER.info("TradeCore Mod wird initialisiert...");

        // API-Client initialisieren
        apiClient = new PriceAPIClient();
    }
}