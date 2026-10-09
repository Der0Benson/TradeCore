package Tradecorev2.client.config;

import Tradecorev2.client.config.ApiEndpointManager.ApiVersion;

public class ModConfig {

    public boolean modEnabled = true;

    // Umbenannt von 'showOnTabOnly' fuer mehr Klarheit
    public boolean showOnShiftOnly = false;

    // API Version Einstellung - nur Cloud ist verfuegbar
    public ApiVersion apiVersion = ApiVersion.CLOUD; // Standardmaessig Cloud

    // Hex-Farbe fuer das Block-Highlighting (Standard: Orange)
    public String highlightColorHex = "#FFAA00";

    /**
     * Setzt die API-Version, verhindert aber das Setzen von 1.8
     */
    public void setApiVersion(ApiVersion version) {
        if (version == ApiVersion.V1_8) {
            // Version 1.8 ist nicht verfuegbar, bleibe bei Cloud
            this.apiVersion = ApiVersion.CLOUD;
        } else {
            this.apiVersion = version;
        }
    }
}
