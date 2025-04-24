package de.tradecore.tradecore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class PriceAPIClient {
    private static final String API_URL = "https://mc-tradecore.de/API/get_price.php";
    private static final long CACHE_TTL_SECONDS = 300; // Cache-Dauer: 5 Minuten
    private final HttpClient client;
    private final ConcurrentHashMap<String, CacheEntry> priceCache;
    private long lastUpdateTimestamp; // Zeitstempel der letzten Cache-Aktualisierung

    public PriceAPIClient() {
        this.client = HttpClient.newHttpClient();
        this.priceCache = new ConcurrentHashMap<>();
        this.lastUpdateTimestamp = 0;
    }

    public PriceResult getItemPrices(String itemName) {
        // Prüfen, ob ein gültiger Cache-Eintrag existiert
        CacheEntry cached = priceCache.get(itemName);
        if (cached != null && !cached.isExpired()) {
            TradeCore.LOGGER.debug("Cache-Treffer für Item: " + itemName);
            return cached.priceResult;
        }

        // Kein gültiger Cache-Eintrag, API aufrufen
        try {
            String url = API_URL + "?item=" + java.net.URLEncoder.encode(itemName, "UTF-8");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                TradeCore.LOGGER.error("API-Anfrage fehlgeschlagen: Status " + response.statusCode());
                return null;
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (json.get("success").getAsBoolean()) {
                int stackpreis = json.get("stackpreis").getAsInt();
                int dkpreis = json.get("dkpreis").getAsInt();
                PriceResult result = new PriceResult(stackpreis, dkpreis);

                // Cache aktualisieren
                priceCache.put(itemName, new CacheEntry(result, Instant.now().getEpochSecond()));
                lastUpdateTimestamp = Instant.now().getEpochSecond(); // Globale Aktualisierungszeit setzen
                return result;
            } else {
                TradeCore.LOGGER.warn("Item nicht gefunden: " + itemName);
                return null;
            }
        } catch (Exception e) {
            TradeCore.LOGGER.error("Fehler beim Abrufen der Preise für " + itemName + ": ", e);
            return null;
        }
    }

    // Getter für die letzte Aktualisierungszeit
    public long getLastUpdateTimestamp() {
        return lastUpdateTimestamp;
    }

    // Methode zum Leeren des Caches
    public void clearCache() {
        priceCache.clear();
        lastUpdateTimestamp = 0;
        TradeCore.LOGGER.info("Cache wurde geleert.");
    }

    // Innere Klasse für Cache-Einträge
    private static class CacheEntry {
        final PriceResult priceResult;
        final long timestamp;

        CacheEntry(PriceResult priceResult, long timestamp) {
            this.priceResult = priceResult;
            this.timestamp = timestamp;
        }

        boolean isExpired() {
            return Instant.now().getEpochSecond() - timestamp > CACHE_TTL_SECONDS;
        }
    }

    public static class PriceResult {
        public final int stackpreis;
        public final int dkpreis;

        public PriceResult(int stackpreis, int dkpreis) {
            this.stackpreis = stackpreis;
            this.dkpreis = dkpreis;
        }
    }
}