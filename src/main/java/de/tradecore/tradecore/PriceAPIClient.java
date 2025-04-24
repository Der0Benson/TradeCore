package de.tradecore.tradecore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement; // Import für allgemeines JSON-Element
import com.google.gson.JsonObject; // Import für JSON Payload
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException; // Import für JSON-Parsing-Fehler
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest; // Wichtig für Header-Nutzung
import java.net.http.HttpRequest.BodyPublishers; // Für POST Body
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration; // Für Timeout
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture; // Für asynchrone Ergebnisse
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class PriceAPIClient {
    // URLs zu den PHP-Skripten
    private static final String API_ALL_PRICES_URL = "https://mc-tradecore.de/API/get_all_prices.php";
    private static final String API_SUBMIT_PRICE_URL = "https://mc-tradecore.de/API/submit_price.php";

    // Pfad zur lokalen JSON-Datei für Preise
    private static final Path PRICE_FILE_PATH = FabricLoader.getInstance().getConfigDir().resolve(TradeCore.MOD_ID + "_prices.json");
    private static final Path PRICE_FILE_TMP_PATH = FabricLoader.getInstance().getConfigDir().resolve(TradeCore.MOD_ID + "_prices.tmp");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Custom Header Konstanten (Wert muss mit PHP übereinstimmen!)
    private static final String CUSTOM_HEADER_NAME = "X-TradeCore-Client";
    private static final String CUSTOM_HEADER_VALUE = "alkj789-GhJkL-MnOpQ";

    private final HttpClient client;
    private final ConcurrentHashMap<String, PriceResult> priceData;
    private final AtomicLong lastUpdateTimestamp = new AtomicLong(0);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "TradeCore-API-Executor");
        thread.setDaemon(true);
        return thread;
    });


    public PriceAPIClient() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.priceData = new ConcurrentHashMap<>();
    }

    // Lädt Preise aus der lokalen Datei beim Start
    public void loadPricesFromDisk() {
        if (Files.exists(PRICE_FILE_PATH)) {
            try (BufferedReader reader = Files.newBufferedReader(PRICE_FILE_PATH)) {
                Type priceMapType = new TypeToken<Map<String, PriceResult>>() {}.getType();
                Map<String, PriceResult> loadedPrices = GSON.fromJson(reader, priceMapType);

                if (loadedPrices != null) {
                    priceData.clear();
                    priceData.putAll(loadedPrices);
                    try {
                        FileTime lastModifiedTime = Files.getLastModifiedTime(PRICE_FILE_PATH);
                        lastUpdateTimestamp.set(lastModifiedTime.toInstant().getEpochSecond());
                        TradeCore.LOGGER.info("Preisdaten ({}) aus {} geladen. Letzte Aktualisierung: {}",
                                priceData.size(), PRICE_FILE_PATH.getFileName(), Instant.ofEpochSecond(lastUpdateTimestamp.get()));
                    } catch (IOException e) { /* Handle or log */ }
                } else { /* Log warning */ }
            } catch (Exception e) { /* Log error, delete corrupt file */ }
        } else { /* Log info */ }
    }

    // Speichert die aktuellen In-Memory-Preise in die lokale Datei
    private void savePricesToDisk() {
        Map<String, PriceResult> pricesToSave = Map.copyOf(priceData);
        try (BufferedWriter writer = Files.newBufferedWriter(PRICE_FILE_TMP_PATH)) {
            GSON.toJson(pricesToSave, writer);
            Files.move(PRICE_FILE_TMP_PATH, PRICE_FILE_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            lastUpdateTimestamp.set(Instant.now().getEpochSecond());
            TradeCore.LOGGER.info("Preisdaten ({}) erfolgreich in {} gespeichert.", pricesToSave.size(), PRICE_FILE_PATH.getFileName());
        } catch (Exception e) { /* Log error, delete tmp file */ }
    }

    // Startet das Abrufen aller Preise asynchron mit dem Custom Header
    public void fetchAllPricesAsync() {
        executor.submit(() -> {
            TradeCore.LOGGER.info("Starte asynchrones Abrufen aller Preise von {}...", API_ALL_PRICES_URL);
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_ALL_PRICES_URL))
                        .header(CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE)
                        .GET()
                        .timeout(Duration.ofSeconds(15))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject(); // Annahme: get_all liefert immer valides JSON oder Fehler
                    if (jsonResponse.has("success") && jsonResponse.get("success").getAsBoolean() && jsonResponse.has("prices")) {
                        JsonObject pricesJson = jsonResponse.getAsJsonObject("prices");
                        Map<String, PriceResult> fetchedPrices = new ConcurrentHashMap<>();
                        Type priceResultType = new TypeToken<PriceResult>() {}.getType();
                        for (Map.Entry<String, com.google.gson.JsonElement> entry : pricesJson.entrySet()) {
                            try {
                                PriceResult priceResult = GSON.fromJson(entry.getValue(), priceResultType);
                                if (priceResult != null) fetchedPrices.put(entry.getKey(), priceResult);
                            } catch (Exception e) { TradeCore.LOGGER.warn("Fehler beim Parsen des Preises für Item '{}': {}", entry.getKey(), e.getMessage()); }
                        }
                        if (!fetchedPrices.isEmpty()) {
                            priceData.clear(); priceData.putAll(fetchedPrices);
                            TradeCore.LOGGER.info("Erfolgreich {} Preise von der API erhalten.", priceData.size());
                            savePricesToDisk();
                        } else { TradeCore.LOGGER.warn("API hat keine gültigen Preisdaten zurückgegeben."); }
                    } else { TradeCore.LOGGER.error("API-Anfrage nicht erfolgreich: {}", jsonResponse.has("message") ? jsonResponse.get("message").getAsString() : "Kein Success/Prices Feld."); }
                } else {
                    if (response.statusCode() == 403) { TradeCore.LOGGER.error("API-Anfrage fehlgeschlagen: Status 403 (Forbidden). Prüfe Custom Header!"); }
                    else { TradeCore.LOGGER.error("API-Anfrage fehlgeschlagen: Status {}", response.statusCode()); }
                }
            } catch (Exception e) { TradeCore.LOGGER.error("Fehler beim Abrufen aller Preise von der API: ", e); }
        });
    }

    // Sendet einen Preisvorschlag mit verbesserter JSON-Prüfung
    public CompletableFuture<Boolean> submitPriceSuggestion(String itemName, int stackPrice, int dkPrice, String playerName, String playerUuid) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        executor.submit(() -> {
            try {
                TradeCore.LOGGER.info("Sende Preisvorschlag für '{}' von '{}'...", itemName, playerName);
                JsonObject payload = new JsonObject();
                payload.addProperty("itemName", itemName);
                payload.addProperty("stackPrice", stackPrice);
                payload.addProperty("dkPrice", dkPrice);
                payload.addProperty("playerName", playerName);
                payload.addProperty("playerUuid", playerUuid);
                String jsonBody = GSON.toJson(payload);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_SUBMIT_PRICE_URL))
                        .header("Content-Type", "application/json")
                        .header(CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE)
                        .POST(BodyPublishers.ofString(jsonBody))
                        .timeout(Duration.ofSeconds(10))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    // Verbesserte Prüfung der Antwort
                    String responseBody = response.body();
                    if (responseBody == null || responseBody.trim().isEmpty() || responseBody.trim().equalsIgnoreCase("null")) {
                        TradeCore.LOGGER.error("Fehler beim Senden des Preisvorschlags: API-Antwort war leer oder null.");
                        future.complete(false);
                        return; // Frühzeitig beenden
                    }

                    try {
                        JsonElement jsonElement = JsonParser.parseString(responseBody); // Erst als allgemeines Element parsen

                        if (jsonElement != null && jsonElement.isJsonObject()) { // Prüfen, ob es ein Objekt ist
                            JsonObject jsonResponse = jsonElement.getAsJsonObject(); // Sicherer Cast
                            boolean success = jsonResponse.has("success") && jsonResponse.get("success").getAsBoolean();
                            if (success) {
                                TradeCore.LOGGER.info("Preisvorschlag erfolgreich gesendet.");
                                future.complete(true);
                            } else {
                                String message = jsonResponse.has("message") ? jsonResponse.get("message").getAsString() : "Unbekannter Fehler laut API.";
                                TradeCore.LOGGER.error("API meldete Fehler beim Senden des Preisvorschlags: {}", message);
                                future.complete(false);
                            }
                        } else {
                            TradeCore.LOGGER.error("Fehler beim Senden des Preisvorschlags: API-Antwort war kein gültiges JSON-Objekt. Antwort: {}", responseBody);
                            future.complete(false);
                        }
                    } catch (JsonSyntaxException jsonEx) {
                        TradeCore.LOGGER.error("Fehler beim Senden des Preisvorschlags: Ungültiges JSON von API erhalten. Antwort: {}", responseBody, jsonEx);
                        future.complete(false);
                    }

                } else {
                    TradeCore.LOGGER.error("Fehler beim Senden des Preisvorschlags: HTTP Status {}", response.statusCode());
                    future.complete(false);
                }

            } catch (Exception e) {
                TradeCore.LOGGER.error("Fehler beim Senden des Preisvorschlags (Netzwerk/Exception): ", e);
                future.complete(false);
            }
        });
        return future;
    }


    // Gibt Preise nur noch aus dem lokalen Speicher zurück
    public PriceResult getItemPrices(String itemName) {
        return priceData.get(itemName);
    }

    // Getter für die letzte Aktualisierungszeit
    public long getLastUpdateTimestamp() {
        return lastUpdateTimestamp.get();
    }

    // Löst eine neue Abfrage aller Preise aus
    public void triggerPriceUpdate() {
        TradeCore.LOGGER.info("Manuelle Preisaktualisierung ausgelöst.");
        fetchAllPricesAsync();
    }

    // Innere Klasse für Preis-Daten
    public static class PriceResult {
        public final int stackpreis;
        public final int dkpreis;
        public PriceResult(int stackpreis, int dkpreis) {
            this.stackpreis = stackpreis; this.dkpreis = dkpreis;
        }
    }

    // Methode zum sauberen Herunterfahren des Executors
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            TradeCore.LOGGER.info("PriceAPIClient Executor heruntergefahren.");
        }
    }
}