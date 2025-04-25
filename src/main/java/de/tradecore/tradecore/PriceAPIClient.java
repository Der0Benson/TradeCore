package de.tradecore.tradecore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate; // Für täglichen Cache
import java.util.Map;
import java.util.Optional; // Optional Importieren
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.RejectedExecutionException;

public class PriceAPIClient {
    // URLs
    private static final String API_ALL_PRICES_URL = "https://mc-tradecore.de/API/get_price.php"; // Deine bestätigte URL
    private static final String API_SUBMIT_PRICE_URL = "https://mc-tradecore.de/API/submit_price.php";
    // NEU: URL für Block des Tages API
    private static final String API_GET_BDT_URL = "https://mc-tradecore.de/API/get_bdt.php"; // Beispiel-URL! Passe ggf. an!
    // NEU: URL für BdT Erstellung
    private static final String API_CREATE_BDT_URL = "https://mc-tradecore.de/API/create_bdt.php"; // Beispiel-URL!

    // Paths & Gson
    private static final Path PRICE_FILE_PATH = FabricLoader.getInstance().getConfigDir().resolve(TradeCore.MOD_ID + "_prices.json");
    private static final Path PRICE_FILE_TMP_PATH = FabricLoader.getInstance().getConfigDir().resolve(TradeCore.MOD_ID + "_prices.tmp");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Custom Header
    private static final String CUSTOM_HEADER_NAME = "X-TradeCore-Client";
    private static final String CUSTOM_HEADER_VALUE = "alkj789-GhJkL-MnOpQ";

    // Member Variables
    private final HttpClient client;
    private final ConcurrentHashMap<String, PriceResult> priceData;
    private final AtomicLong lastUpdateTimestamp = new AtomicLong(0);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "TradeCore-API-Executor");
        thread.setDaemon(true);
        return thread;
    });

    // Cache für Block des Tages Ergebnis
    private BlockOfTheDayResult cachedBdtResult = null;
    private LocalDate bdtCacheDate = null;

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
                    priceData.clear(); priceData.putAll(loadedPrices);
                    try {
                        FileTime lastModifiedTime = Files.getLastModifiedTime(PRICE_FILE_PATH);
                        lastUpdateTimestamp.set(lastModifiedTime.toInstant().getEpochSecond());
                        TradeCore.LOGGER.info("Preisdaten ({}) geladen. Stand: {}", priceData.size(), Instant.ofEpochSecond(lastUpdateTimestamp.get()));
                    } catch (IOException e) { lastUpdateTimestamp.set(Instant.now().getEpochSecond()); }
                } else { TradeCore.LOGGER.warn("Preisdatei leer/ungültig."); lastUpdateTimestamp.set(0); }
            } catch (Exception e) { TradeCore.LOGGER.error("Fehler beim Laden der Preisdatei: ", e); priceData.clear(); lastUpdateTimestamp.set(0); try { Files.deleteIfExists(PRICE_FILE_PATH); } catch (IOException ex) {}}
        } else { TradeCore.LOGGER.info("Keine lokale Preisdatei gefunden."); lastUpdateTimestamp.set(0); }
    }

    // Speichert die aktuellen In-Memory-Preise in die lokale Datei
    private void savePricesToDisk() {
        Map<String, PriceResult> pricesToSave = Map.copyOf(priceData);
        try (BufferedWriter writer = Files.newBufferedWriter(PRICE_FILE_TMP_PATH)) {
            GSON.toJson(pricesToSave, writer);
            Files.move(PRICE_FILE_TMP_PATH, PRICE_FILE_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            lastUpdateTimestamp.set(Instant.now().getEpochSecond());
            TradeCore.LOGGER.info("Preisdaten ({}) gespeichert.", pricesToSave.size());
        } catch (Exception e) { TradeCore.LOGGER.error("Fehler beim Speichern der Preisdatei: ", e); try { Files.deleteIfExists(PRICE_FILE_TMP_PATH); } catch (IOException ex) {}}
    }

    // Startet das Abrufen aller Preise asynchron mit Prüfung
    public void fetchAllPricesAsync() {
        if (executor.isShutdown() || executor.isTerminated()) {
            TradeCore.LOGGER.warn("fetchAllPricesAsync: Executor heruntergefahren."); return;
        }
        executor.submit(() -> {
            if (Thread.currentThread().isInterrupted()) { return; }
            TradeCore.LOGGER.info("Starte Preisabruf von {}...", API_ALL_PRICES_URL);
            try {
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(API_ALL_PRICES_URL)).header(CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE).GET().timeout(Duration.ofSeconds(15)).build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (jsonResponse.has("success") && jsonResponse.get("success").getAsBoolean() && jsonResponse.has("prices")) {
                        JsonObject pricesJson = jsonResponse.getAsJsonObject("prices");
                        Map<String, PriceResult> fetchedPrices = new ConcurrentHashMap<>();
                        Type priceResultType = new TypeToken<PriceResult>() {}.getType();
                        for (Map.Entry<String, JsonElement> entry : pricesJson.entrySet()) {
                            try {
                                PriceResult priceResult = GSON.fromJson(entry.getValue(), priceResultType);
                                if (priceResult != null) fetchedPrices.put(entry.getKey(), priceResult);
                            } catch (Exception e) { TradeCore.LOGGER.warn("Parse Fehler für Item '{}': {}", entry.getKey(), e.getMessage()); }
                        }
                        if (!fetchedPrices.isEmpty()) {
                            priceData.clear(); priceData.putAll(fetchedPrices);
                            TradeCore.LOGGER.info("{} Preise von API erhalten.", priceData.size());
                            savePricesToDisk();
                        } else { TradeCore.LOGGER.warn("API lieferte keine Preisdaten."); }
                    } else { TradeCore.LOGGER.error("API Fehler: {}", jsonResponse.has("message") ? jsonResponse.get("message").getAsString() : "Kein Success/Prices Feld."); }
                } else { if (response.statusCode() == 403) TradeCore.LOGGER.error("Preisabruf HTTP 403 (Forbidden). Prüfe Header!"); else TradeCore.LOGGER.error("Preisabruf HTTP Fehler: {}", response.statusCode());}
            } catch (Exception e) {
                if (!(e instanceof InterruptedException)) { TradeCore.LOGGER.error("Fehler beim Preisabruf: ", e); }
                else { Thread.currentThread().interrupt(); }
            }
        });
    }

    // Sendet einen Preisvorschlag
    public CompletableFuture<Boolean> submitPriceSuggestion(String itemName, int stackPrice, int dkPrice, String playerName, String playerUuid) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (executor.isShutdown() || executor.isTerminated()) {
            TradeCore.LOGGER.warn("submitPriceSuggestion: Executor heruntergefahren."); future.complete(false); return future;
        }
        executor.submit(() -> {
            if (Thread.currentThread().isInterrupted()) { future.complete(false); return; }
            try {
                TradeCore.LOGGER.info("Sende Preisvorschlag: {} von {}", itemName, playerName);
                JsonObject payload = new JsonObject(); payload.addProperty("itemName", itemName); payload.addProperty("stackPrice", stackPrice); payload.addProperty("dkPrice", dkPrice); payload.addProperty("playerName", playerName); payload.addProperty("playerUuid", playerUuid);
                String jsonBody = GSON.toJson(payload);
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(API_SUBMIT_PRICE_URL)).header("Content-Type", "application/json").header(CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE).POST(BodyPublishers.ofString(jsonBody)).timeout(Duration.ofSeconds(10)).build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String responseBody = response.body();
                    if (responseBody == null || responseBody.trim().isEmpty() || responseBody.trim().equalsIgnoreCase("null")) { TradeCore.LOGGER.error("Submit Error: API response empty/null."); future.complete(false); return; }
                    try {
                        JsonElement jsonElement = JsonParser.parseString(responseBody);
                        if (jsonElement != null && jsonElement.isJsonObject()) {
                            JsonObject jsonResponse = jsonElement.getAsJsonObject(); boolean success = jsonResponse.has("success") && jsonResponse.get("success").getAsBoolean();
                            if (success) { future.complete(true); }
                            else { TradeCore.LOGGER.error("Submit Error: API reports failure: {}", jsonResponse.has("message") ? jsonResponse.get("message").getAsString() : "Unknown"); future.complete(false); }
                        } else { TradeCore.LOGGER.error("Submit Error: Invalid JSON object received: {}", responseBody); future.complete(false); }
                    } catch (JsonSyntaxException jsonEx) { TradeCore.LOGGER.error("Submit Error: Invalid JSON syntax: {}", responseBody, jsonEx); future.complete(false); }
                } else { TradeCore.LOGGER.error("Submit Error: HTTP Status {}", response.statusCode()); future.complete(false); }
            } catch (Exception e) {
                if (!(e instanceof InterruptedException)) { TradeCore.LOGGER.error("Submit Error (Network/Exception): ", e); }
                else { Thread.currentThread().interrupt(); }
                future.complete(false);
            }
        });
        return future;
    }

    // Gibt Preise aus dem Speicher zurück
    public PriceResult getItemPrices(String itemName) {
        return priceData.get(itemName);
    }

    // Getter für den Zeitstempel der Preisdaten
    public long getLastUpdateTimestamp() {
        return lastUpdateTimestamp.get();
    }

    // Löst eine manuelle Preisaktualisierung aus UND speichert den Cooldown-Timestamp
    public void triggerPriceUpdate() {
        TradeCore.LOGGER.info("Manuelle Preisaktualisierung ausgelöst.");
        TradeCoreConfig.updateAndSaveLastFetchTimestamp(System.currentTimeMillis());
        fetchAllPricesAsync();
    }

    // Löscht die lokale Preisdatei
    public void deletePriceFile() {
        try {
            boolean deleted = Files.deleteIfExists(PRICE_FILE_PATH);
            if (deleted) TradeCore.LOGGER.info("Preisdatei {} gelöscht.", PRICE_FILE_PATH.getFileName());
            Files.deleteIfExists(PRICE_FILE_TMP_PATH);
        } catch (IOException | SecurityException e) { TradeCore.LOGGER.error("Fehler beim Löschen der Preisdatei {}: ", PRICE_FILE_PATH.getFileName(), e); }
        priceData.clear(); lastUpdateTimestamp.set(0);
        TradeCore.LOGGER.info("In-Memory Preisdaten geleert.");
    }

    // Innere Klasse für Preis-Daten
    public static class PriceResult {
        public final int stackpreis; public final int dkpreis;
        public PriceResult(int stackpreis, int dkpreis) { this.stackpreis = stackpreis; this.dkpreis = dkpreis; }
    }

    // Stoppt den Executor (mit interner Prüfung)
    public void shutdown() {
        if (executor != null && !executor.isShutdown() && !executor.isTerminated()) {
            executor.shutdown(); TradeCore.LOGGER.info("PriceAPIClient Executor shutdown initiated.");
        }
    }

    // --- METHODEN UND KLASSE FÜR BLOCK DES TAGES ---

    // Innere Klasse für das Ergebnis
    public static class BlockOfTheDayResult {
        public final String itemName; // Hier steht der Anzeigename oder die ID, je nach API
        public final String gewinn;
        public final String message;
        public final boolean found;

        public BlockOfTheDayResult(String itemName, String gewinn) { this.itemName = itemName; this.gewinn = gewinn; this.message = null; this.found = true; }
        public BlockOfTheDayResult(String message) { this.itemName = "Nicht gefunden"; this.gewinn = "-"; this.message = message; this.found = false; }
    }

    // Methode zum Abrufen des Block des Tages (mit Cache)
    public CompletableFuture<BlockOfTheDayResult> fetchBlockOfTheDayAsync() {
        LocalDate today = LocalDate.now();
        if (cachedBdtResult != null && today.equals(bdtCacheDate)) {
            TradeCore.LOGGER.info("Block des Tages aus Cache geladen.");
            return CompletableFuture.completedFuture(cachedBdtResult);
        }

        CompletableFuture<BlockOfTheDayResult> future = new CompletableFuture<>();
        if (executor.isShutdown() || executor.isTerminated()) {
            TradeCore.LOGGER.warn("fetchBlockOfTheDayAsync: Executor heruntergefahren.");
            future.complete(new BlockOfTheDayResult("Fehler: Client heruntergefahren")); return future;
        }

        executor.submit(() -> {
            if (Thread.currentThread().isInterrupted()) { future.complete(new BlockOfTheDayResult("Fehler: Thread unterbrochen")); return; }
            TradeCore.LOGGER.info("Rufe Block des Tages von {} ab...", API_GET_BDT_URL);
            try {
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(API_GET_BDT_URL)).header(CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE).GET().timeout(Duration.ofSeconds(10)).build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                BlockOfTheDayResult result;

                if (response.statusCode() == 200) {
                    String responseBody = response.body();
                    if (responseBody == null || responseBody.trim().isEmpty() || responseBody.trim().equalsIgnoreCase("null")) {
                        result = new BlockOfTheDayResult("Fehler: Leere Server-Antwort");
                    } else {
                        try {
                            JsonElement jsonElement = JsonParser.parseString(responseBody);
                            if (jsonElement.isJsonObject()){
                                JsonObject jsonResponse = jsonElement.getAsJsonObject();
                                if (jsonResponse.has("success") && jsonResponse.get("success").getAsBoolean()) {
                                    // Lese item_name und gewinn aus der Antwort
                                    String itemName = jsonResponse.has("item_name") ? jsonResponse.get("item_name").getAsString() : "Unbekannt";
                                    String gewinn = jsonResponse.has("gewinn") ? jsonResponse.get("gewinn").getAsString() : "-";
                                    result = new BlockOfTheDayResult(itemName, gewinn);
                                    TradeCore.LOGGER.info("Block des Tages erhalten: {}", itemName);
                                } else {
                                    String message = jsonResponse.has("message") ? jsonResponse.get("message").getAsString() : "Kein Block gefunden.";
                                    result = new BlockOfTheDayResult(message);
                                    TradeCore.LOGGER.info("Kein Block des Tages: {}", message);
                                }
                            } else {
                                TradeCore.LOGGER.error("Fehler beim Parsen der BdT JSON: Kein JSON Objekt. Body: {}", responseBody);
                                result = new BlockOfTheDayResult("Fehler: Ungültiges JSON-Format");
                            }
                        } catch (JsonSyntaxException | IllegalStateException jsonEx) {
                            TradeCore.LOGGER.error("Fehler beim Parsen der BdT JSON: {}", responseBody, jsonEx);
                            result = new BlockOfTheDayResult("Fehler: Ungültige Server-Antwort");
                        }
                    }
                } else {
                    TradeCore.LOGGER.error("Fehler beim Abrufen des BdT: HTTP Status {}", response.statusCode());
                    result = new BlockOfTheDayResult("Fehler: Server nicht erreichbar (HTTP " + response.statusCode() + ")");
                }

                // Cache aktualisieren und Ergebnis abschließen
                this.cachedBdtResult = result;
                this.bdtCacheDate = today;
                future.complete(result);

            } catch (Exception e) {
                if (!(e instanceof InterruptedException)) { TradeCore.LOGGER.error("Fehler beim Abrufen des BdT: ", e); }
                else { Thread.currentThread().interrupt(); }
                future.complete(new BlockOfTheDayResult("Fehler: Netzwerkproblem"));
            }
        });
        return future;
    }

    // --- NEUE METHODE ZUM ERSTELLEN DES BLOCK DES TAGES ---
    // Akzeptiert jetzt itemDisplayName statt itemName
    public CompletableFuture<Boolean> createBlockOfTheDayAsync(String itemDisplayName, String gewinn, String playerUuid) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (executor.isShutdown() || executor.isTerminated()) {
            TradeCore.LOGGER.warn("createBlockOfTheDayAsync: Executor heruntergefahren.");
            future.complete(false); return future;
        }

        executor.submit(() -> {
            if (Thread.currentThread().isInterrupted()) { future.complete(false); return; }
            try {
                TradeCore.LOGGER.info("Sende neuen Block des Tages: {} (Gewinn: {}) von {}", itemDisplayName, gewinn, playerUuid);
                JsonObject payload = new JsonObject();
                // Sende den Anzeigenamen unter dem Schlüssel "itemDisplayName"
                payload.addProperty("itemDisplayName", itemDisplayName);
                payload.addProperty("gewinn", gewinn);
                payload.addProperty("playerUuid", playerUuid);
                String jsonBody = GSON.toJson(payload);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_CREATE_BDT_URL)) // Neue URL
                        .header("Content-Type", "application/json")
                        .header(CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE) // Header
                        .POST(BodyPublishers.ofString(jsonBody))
                        .timeout(Duration.ofSeconds(10))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String responseBody = response.body();
                    if (responseBody == null || responseBody.trim().isEmpty() || responseBody.trim().equalsIgnoreCase("null")) {
                        TradeCore.LOGGER.error("Create BdT Error: API response empty/null."); future.complete(false); return;
                    }
                    try {
                        JsonElement jsonElement = JsonParser.parseString(responseBody);
                        if (jsonElement != null && jsonElement.isJsonObject()) {
                            JsonObject jsonResponse = jsonElement.getAsJsonObject();
                            boolean success = jsonResponse.has("success") && jsonResponse.get("success").getAsBoolean();
                            if (success) {
                                TradeCore.LOGGER.info("Block des Tages erfolgreich erstellt.");
                                this.cachedBdtResult = null; this.bdtCacheDate = null; // Cache leeren
                                future.complete(true);
                            } else {
                                String message = jsonResponse.has("message") ? jsonResponse.get("message").getAsString() : "Unbekannter API Fehler.";
                                TradeCore.LOGGER.error("Create BdT Error: API reports failure: {}", message);
                                future.complete(false);
                            }
                        } else { TradeCore.LOGGER.error("Create BdT Error: Invalid JSON Object"); future.complete(false); }
                    } catch (JsonSyntaxException jsonEx) { TradeCore.LOGGER.error("Create BdT Error: Invalid JSON Syntax", jsonEx); future.complete(false); }
                } else {
                    TradeCore.LOGGER.error("Create BdT Error: HTTP Status {} - Body: {}", response.statusCode(), response.body());
                    future.complete(false);
                }
            } catch (Exception e) {
                if (!(e instanceof InterruptedException)) { TradeCore.LOGGER.error("Create BdT Error (Network/Exception): ", e); }
                else { Thread.currentThread().interrupt(); }
                future.complete(false);
            }
        });
        return future;
    }
}