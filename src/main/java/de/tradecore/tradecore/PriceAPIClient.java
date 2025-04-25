package de.tradecore.tradecore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient; // Für thenAcceptAsync auf dem Client-Thread

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
    private static final String API_ALL_PRICES_URL = "https://mc-tradecore.de/API/get_price.php";
    private static final String API_SUBMIT_PRICE_URL = "https://mc-tradecore.de/API/submit_price.php";
    private static final String API_GET_BDT_URL = "https://mc-tradecore.de/API/get_bdt.php";
    private static final String API_CREATE_BDT_URL = "https://mc-tradecore.de/API/create_bdt.php";
    private static final String API_SUBMIT_BDT_VOTE_URL = "https://mc-tradecore.de/API/submit_bdt_vote.php";

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
        thread.setDaemon(true); // Wichtig: Ist als Daemon gesetzt!
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
                HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString()); // this.client verwenden
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
                HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString()); // this.client verwenden

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

    // Stoppt den Executor nicht mehr explizit, da Daemon Thread
    public void shutdown() {
        // executor.shutdown(); // Nicht mehr nötig, da Daemon Thread
        TradeCore.LOGGER.info("PriceAPIClient shutdown() aufgerufen (Executor wird nicht mehr explizit gestoppt, da Daemon).");
    }

    // --- METHODEN UND KLASSE FÜR BLOCK DES TAGES (ERWEITERT) ---

    public static class BlockOfTheDayResult {
        public final String bdtId;
        public final String itemName;
        public final String gewinn;
        public final String message;
        public final boolean found;
        public final int schnellVotes;
        public final int langsamVotes;
        public final boolean hasVoted;

        public BlockOfTheDayResult(String bdtId, String itemName, String gewinn, int schnellVotes, int langsamVotes, boolean hasVoted) {
            this.bdtId = bdtId; this.itemName = itemName; this.gewinn = gewinn;
            this.schnellVotes = schnellVotes; this.langsamVotes = langsamVotes; this.hasVoted = hasVoted;
            this.message = null; this.found = true;
        }
        public BlockOfTheDayResult(String message) {
            this.bdtId = null; this.itemName = "Nicht gefunden"; this.gewinn = "-";
            this.schnellVotes = 0; this.langsamVotes = 0; this.hasVoted = false;
            this.message = message; this.found = false;
        }
    }

    // Methode zum Abrufen des Block des Tages (nutzt Cache, wenn möglich)
    public CompletableFuture<BlockOfTheDayResult> fetchBlockOfTheDayAsync() {
        LocalDate today = LocalDate.now();
        if (cachedBdtResult != null && today.equals(bdtCacheDate)) {
            TradeCore.LOGGER.info("Block des Tages aus Cache geladen (Achtung: Votestatus könnte veraltet sein).");
            return CompletableFuture.completedFuture(cachedBdtResult);
        }
        // Wenn kein Cache, rufe die Force-Methode auf
        return forceFetchBlockOfTheDayAsync();
    }

    // Methode zum Abrufen des Block des Tages (ERZWINGT Neuladen vom Server)
    public CompletableFuture<BlockOfTheDayResult> forceFetchBlockOfTheDayAsync() {
        TradeCore.LOGGER.info("Erzwungenes Neuladen des Block des Tages gestartet...");

        CompletableFuture<BlockOfTheDayResult> future = new CompletableFuture<>();
        if (executor.isShutdown() || executor.isTerminated()) {
            TradeCore.LOGGER.warn("forceFetchBlockOfTheDayAsync: Executor heruntergefahren.");
            future.complete(new BlockOfTheDayResult("Fehler: Client heruntergefahren"));
            return future;
        }

        String playerUuid = "";
        MinecraftClient mcClient = MinecraftClient.getInstance();
        if (mcClient != null && mcClient.player != null) {
            playerUuid = mcClient.player.getUuidAsString();
        } else {
            TradeCore.LOGGER.warn("Konnte Player UUID für BdT-Abruf nicht ermitteln.");
        }
        final String finalPlayerUuid = playerUuid;

        executor.submit(() -> {
            if (Thread.currentThread().isInterrupted()) {
                future.complete(new BlockOfTheDayResult("Fehler: Thread unterbrochen"));
                return;
            }
            TradeCore.LOGGER.info("Erzwungenes Neuladen: Rufe Block des Tages von {} ab...", API_GET_BDT_URL);
            try {
                String urlWithParams = API_GET_BDT_URL + (finalPlayerUuid.isEmpty() ? "" : "?playerUuid=" + finalPlayerUuid);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlWithParams))
                        .header(CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE)
                        .GET().timeout(Duration.ofSeconds(10)).build();

                HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString()); // this.client verwenden
                BlockOfTheDayResult result;
                LocalDate today = LocalDate.now();

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
                                    String bdtId = jsonResponse.has("bdt_id") ? jsonResponse.get("bdt_id").getAsString() : null;
                                    String itemName = jsonResponse.has("item_name") ? jsonResponse.get("item_name").getAsString() : "Unbekannt";
                                    String gewinn = jsonResponse.has("gewinn") ? jsonResponse.get("gewinn").getAsString() : "-";
                                    int schnellVotes = jsonResponse.has("schnell_votes") ? jsonResponse.get("schnell_votes").getAsInt() : 0;
                                    int langsamVotes = jsonResponse.has("langsam_votes") ? jsonResponse.get("langsam_votes").getAsInt() : 0;
                                    boolean hasVoted = jsonResponse.has("has_voted") ? jsonResponse.get("has_voted").getAsBoolean() : false;

                                    if (bdtId == null) {
                                        TradeCore.LOGGER.error("BdT von API erhalten (force fetch), aber ohne 'bdt_id'!");
                                        result = new BlockOfTheDayResult("Fehler: Ungültige Serverdaten (fehlende ID)");
                                    } else {
                                        result = new BlockOfTheDayResult(bdtId, itemName, gewinn, schnellVotes, langsamVotes, hasVoted);
                                        TradeCore.LOGGER.info("Block des Tages ({}) erhalten (force fetch): {}", bdtId, itemName);
                                    }
                                } else {
                                    String message = jsonResponse.has("message") ? jsonResponse.get("message").getAsString() : "Kein Block gefunden.";
                                    result = new BlockOfTheDayResult(message);
                                    TradeCore.LOGGER.info("Kein Block des Tages (force fetch): {}", message);
                                }
                            } else {
                                TradeCore.LOGGER.error("Fehler beim Parsen der BdT JSON (force fetch): Kein JSON Objekt. Body: {}", responseBody);
                                result = new BlockOfTheDayResult("Fehler: Ungültiges JSON-Format");
                            }
                        } catch (JsonSyntaxException | IllegalStateException | ClassCastException jsonEx) {
                            TradeCore.LOGGER.error("Fehler beim Parsen der BdT JSON (force fetch): {}", responseBody, jsonEx);
                            result = new BlockOfTheDayResult("Fehler: Ungültige Server-Antwort");
                        }
                    }
                } else {
                    TradeCore.LOGGER.error("Fehler beim Abrufen des BdT (force fetch): HTTP Status {}", response.statusCode());
                    result = new BlockOfTheDayResult("Fehler: Server nicht erreichbar (HTTP " + response.statusCode() + ")");
                }

                // Cache immer aktualisieren
                this.cachedBdtResult = result;
                this.bdtCacheDate = today;
                future.complete(result);

            } catch (Exception e) {
                if (!(e instanceof InterruptedException)) { TradeCore.LOGGER.error("Fehler beim Abrufen des BdT (force fetch): ", e); }
                else { Thread.currentThread().interrupt(); }
                future.complete(new BlockOfTheDayResult("Fehler: Netzwerkproblem"));
            }
        });
        return future;
    }

    // --- METHODE ZUM ERSTELLEN DES BLOCK DES TAGES ---
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
                payload.addProperty("itemDisplayName", itemDisplayName);
                payload.addProperty("gewinn", gewinn);
                payload.addProperty("playerUuid", playerUuid);
                String jsonBody = GSON.toJson(payload);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_CREATE_BDT_URL))
                        .header("Content-Type", "application/json")
                        .header(CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE)
                        .POST(BodyPublishers.ofString(jsonBody))
                        .timeout(Duration.ofSeconds(10)).build();
                HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString()); // this.client verwenden

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
                                this.cachedBdtResult = null; this.bdtCacheDate = null;
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

    // --- METHODE ZUM SENDEN EINER BDT ABSTIMMUNG ---
    public CompletableFuture<Boolean> submitBdtVoteAsync(String bdtId, String playerUuid, String voteType) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (executor.isShutdown() || executor.isTerminated()) {
            TradeCore.LOGGER.warn("submitBdtVoteAsync: Executor heruntergefahren.");
            future.complete(false); return future;
        }
        if (!("schnell".equals(voteType) || "langsam".equals(voteType))) {
            TradeCore.LOGGER.error("Ungültiger voteType: {}", voteType);
            future.complete(false); return future;
        }
        if (bdtId == null || bdtId.trim().isEmpty()) {
            TradeCore.LOGGER.error("Ungültige bdtId für Abstimmung.");
            future.complete(false); return future;
        }

        executor.submit(() -> {
            if (Thread.currentThread().isInterrupted()) { future.complete(false); return; }
            try {
                TradeCore.LOGGER.info("Sende BdT-Abstimmung: BdT ID {}, Spieler {}, Stimme {}", bdtId, playerUuid, voteType);
                JsonObject payload = new JsonObject();
                payload.addProperty("bdtId", bdtId);
                payload.addProperty("playerUuid", playerUuid);
                payload.addProperty("voteType", voteType);
                String jsonBody = GSON.toJson(payload);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_SUBMIT_BDT_VOTE_URL))
                        .header("Content-Type", "application/json")
                        .header(CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE)
                        .POST(BodyPublishers.ofString(jsonBody))
                        .timeout(Duration.ofSeconds(10)).build();
                HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString()); // this.client verwenden

                if (response.statusCode() == 200) {
                    String responseBody = response.body();
                    if (responseBody == null || responseBody.trim().isEmpty() || responseBody.trim().equalsIgnoreCase("null")) {
                        TradeCore.LOGGER.error("Submit Vote Error: API response empty/null."); future.complete(false); return;
                    }
                    try {
                        JsonElement jsonElement = JsonParser.parseString(responseBody);
                        if (jsonElement != null && jsonElement.isJsonObject()) {
                            JsonObject jsonResponse = jsonElement.getAsJsonObject();
                            boolean success = jsonResponse.has("success") && jsonResponse.get("success").getAsBoolean();
                            if (success) {
                                TradeCore.LOGGER.info("Abstimmung erfolgreich gesendet.");
                                future.complete(true);
                            } else {
                                String message = jsonResponse.has("message") ? jsonResponse.get("message").getAsString() : "Unbekannter API Fehler.";
                                TradeCore.LOGGER.error("Submit Vote Error: API reports failure: {}", message);
                                future.complete(false);
                            }
                        } else { TradeCore.LOGGER.error("Submit Vote Error: Invalid JSON Object"); future.complete(false); }
                    } catch (JsonSyntaxException jsonEx) { TradeCore.LOGGER.error("Submit Vote Error: Invalid JSON Syntax", jsonEx); future.complete(false); }
                } else {
                    TradeCore.LOGGER.error("Submit Vote Error: HTTP Status {} - Body: {}", response.statusCode(), response.body());
                    future.complete(false);
                }
            } catch (Exception e) {
                if (!(e instanceof InterruptedException)) { TradeCore.LOGGER.error("Submit Vote Error (Network/Exception): ", e); }
                else { Thread.currentThread().interrupt(); }
                future.complete(false);
            }
        });
        return future;
    }
} // Ende der Klasse PriceAPIClient