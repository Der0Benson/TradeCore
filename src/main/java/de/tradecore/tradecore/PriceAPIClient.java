package de.tradecore.tradecore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

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
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

public class PriceAPIClient {

    private static final String API_ALL_PRICES_URL = "https://mc-tradecore.de/API/get_price.php";
    private static final String API_SUBMIT_PRICE_URL = "https://mc-tradecore.de/API/submit_price.php";
    private static final String API_GET_BDT_URL = "https://mc-tradecore.de/API/get_bdt.php";
    private static final String API_CREATE_BDT_URL = "https://mc-tradecore.de/API/create_bdt.php";
    private static final String API_SUBMIT_BDT_VOTE_URL = "https://mc-tradecore.de/API/submit_bdt_vote.php";
    private static final String API_UPDATE_BDT_URL = "https://mc-tradecore.de/API/update_bdt.php";
    private static final String API_CLAIM_DISCORD_URL = "https://mc-tradecore.de/API/UUIDsubmit.php";
    private static final String API_GET_USER_LEVEL_URL = "https://mc-tradecore.de/API/get_user_level.php"; // NEU

    private static final Path PRICE_FILE_PATH = FabricLoader.getInstance().getConfigDir().resolve(TradeCore.MOD_ID + "_prices.json");
    private static final Path PRICE_FILE_TMP_PATH = FabricLoader.getInstance().getConfigDir().resolve(TradeCore.MOD_ID + "_prices.tmp");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

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

    private BlockOfTheDayResult cachedBdtResult = null;
    private LocalDate bdtCacheDate = null;

    public PriceAPIClient() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.priceData = new ConcurrentHashMap<>();
    }

    public void loadPricesFromDisk() {
        if (Files.exists(PRICE_FILE_PATH)) {
            try (BufferedReader reader = Files.newBufferedReader(PRICE_FILE_PATH)) {
                Type priceMapType = new TypeToken<Map<String, PriceResult>>() {}.getType();
                Map<String, PriceResult> loadedPrices = GSON.fromJson(reader, priceMapType); // Gson kümmert sich um die Felder
                if (loadedPrices != null) {
                    priceData.clear();
                    // Wenn 'stueckpreis' in der JSON-Datei fehlt, initialisiert Gson es mit 0 für int.
                    // Das ist das gewünschte Verhalten.
                    priceData.putAll(loadedPrices);
                    try {
                        FileTime lastModifiedTime = Files.getLastModifiedTime(PRICE_FILE_PATH);
                        lastUpdateTimestamp.set(lastModifiedTime.toInstant().getEpochSecond());
                        TradeCore.LOGGER.info("Preisdaten ({}) geladen. Stand: {}", priceData.size(), Instant.ofEpochSecond(lastUpdateTimestamp.get()));
                    } catch (IOException e) {
                        lastUpdateTimestamp.set(Instant.now().getEpochSecond());
                    }
                } else {
                    TradeCore.LOGGER.warn("Preisdatei leer/ungültig.");
                    lastUpdateTimestamp.set(0);
                }
            } catch (IOException | JsonSyntaxException e) {
                TradeCore.LOGGER.error("Fehler beim Laden der Preisdatei: ", e);
                priceData.clear();
                lastUpdateTimestamp.set(0);
                try {
                    Files.deleteIfExists(PRICE_FILE_PATH);
                } catch (IOException ex) {
                    TradeCore.LOGGER.error("Konnte korrupte Preisdatei nicht löschen: ", ex);
                }
            }
        } else {
            TradeCore.LOGGER.info("Keine lokale Preisdatei gefunden.");
            lastUpdateTimestamp.set(0);
        }
    }

    private void savePricesToDisk() {
        Map<String, PriceResult> pricesToSave = Map.copyOf(priceData);
        try (BufferedWriter writer = Files.newBufferedWriter(PRICE_FILE_TMP_PATH)) {
            GSON.toJson(pricesToSave, writer);
            Files.move(PRICE_FILE_TMP_PATH, PRICE_FILE_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            lastUpdateTimestamp.set(Instant.now().getEpochSecond());
            TradeCore.LOGGER.info("Preisdaten ({}) gespeichert.", pricesToSave.size());
        } catch (IOException e) {
            TradeCore.LOGGER.error("Fehler beim Speichern der Preisdatei: ", e);
            try {
                Files.deleteIfExists(PRICE_FILE_TMP_PATH);
            } catch (IOException ex) {
                TradeCore.LOGGER.error("Konnte temporäre Preisdatei nicht löschen: ", ex);
            }
        }
    }

    public void fetchAllPricesAsync() {
        if (executor.isShutdown() || executor.isTerminated()) {
            TradeCore.LOGGER.warn("fetchAllPricesAsync: Executor heruntergefahren.");
            return;
        }
        try {
            executor.submit(() -> {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                TradeCore.LOGGER.info("Starte Preisabruf von {}...", API_ALL_PRICES_URL);
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(API_ALL_PRICES_URL))
                            .header(CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE)
                            .GET()
                            .timeout(Duration.ofSeconds(15))
                            .build();
                    HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                        if (jsonResponse.has("success") && jsonResponse.get("success").getAsBoolean() && jsonResponse.has("prices")) {
                            JsonObject pricesJson = jsonResponse.getAsJsonObject("prices");
                            Map<String, PriceResult> fetchedPrices = new ConcurrentHashMap<>();
                            Type priceResultType = new TypeToken<PriceResult>() {}.getType(); // Typ bleibt PriceResult
                            for (Map.Entry<String, JsonElement> entry : pricesJson.entrySet()) {
                                try {
                                    JsonObject itemPriceJson = entry.getValue().getAsJsonObject();
                                    int stackpreis = itemPriceJson.has("stackpreis") ? itemPriceJson.get("stackpreis").getAsInt() : 0;
                                    int dkpreis = itemPriceJson.has("dkpreis") ? itemPriceJson.get("dkpreis").getAsInt() : 0;
                                    int stueckpreis = itemPriceJson.has("stueckpreis") ? itemPriceJson.get("stueckpreis").getAsInt() : 0;

                                    fetchedPrices.put(entry.getKey(), new PriceResult(stackpreis, dkpreis, stueckpreis));
                                } catch (Exception e) {
                                    TradeCore.LOGGER.warn("Parse Fehler für Item '{}': {}", entry.getKey(), e.getMessage());
                                }
                            }
                            if (!fetchedPrices.isEmpty()) {
                                priceData.clear();
                                priceData.putAll(fetchedPrices);
                                TradeCore.LOGGER.info("{} Preise von API erhalten.", fetchedPrices.size());
                                savePricesToDisk();
                            } else {
                                TradeCore.LOGGER.warn("API lieferte keine Preisdaten.");
                            }
                        } else {
                            String message = jsonResponse.has("message") ? jsonResponse.get("message").getAsString() : "Unknown error";
                            TradeCore.LOGGER.error("Preisabruf fehlgeschlagen: {}", message);
                        }
                    } else {
                        if (response.statusCode() == 403) {
                            TradeCore.LOGGER.error("Preisabruf HTTP 403 (Forbidden). Prüfe Header!");
                        } else {
                            TradeCore.LOGGER.error("Preisabruf HTTP Fehler: {}", response.statusCode());
                        }
                    }
                } catch (Exception e) {
                    if (!(e instanceof InterruptedException)) {
                        TradeCore.LOGGER.error("Fehler beim Preisabruf: ", e);
                    } else {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            TradeCore.LOGGER.error("Executor is shut down, cannot execute fetchAllPricesAsync", e);
        }
    }

    public CompletableFuture<Boolean> submitPriceSuggestion(String itemName, int stueckPreis, int stackPrice, int dkPrice, String playerName, String playerUuid) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (executor.isShutdown() || executor.isTerminated()) {
            TradeCore.LOGGER.warn("submitPriceSuggestion: Executor heruntergefahren.");
            future.complete(false);
            return future;
        }
        try {
            executor.submit(() -> {
                if (Thread.currentThread().isInterrupted()) {
                    future.complete(false);
                    return;
                }
                try {
                    TradeCore.LOGGER.info("Sende Preisvorschlag: {} von {} (Stück: {}, Stack: {}, DK: {})", itemName, playerName, stueckPreis, stackPrice, dkPrice);
                    JsonObject payload = new JsonObject();
                    payload.addProperty("itemName", itemName);
                    payload.addProperty("stueckPreis", stueckPreis);
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
                    HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        String responseBody = response.body();
                        if (responseBody == null || responseBody.trim().isEmpty() || responseBody.trim().equalsIgnoreCase("null")) {
                            TradeCore.LOGGER.error("Submit Error: API response empty/null.");
                            future.complete(false);
                            return;
                        }
                        try {
                            JsonElement jsonElement = JsonParser.parseString(responseBody);
                            if (jsonElement != null && jsonElement.isJsonObject()) {
                                JsonObject jsonResponse = jsonElement.getAsJsonObject();
                                boolean success = jsonResponse.has("success") && jsonResponse.get("success").getAsBoolean();
                                if (success) {
                                    future.complete(true);
                                } else {
                                    String message = jsonResponse.has("message") ? jsonResponse.get("message").getAsString() : "Unknown";
                                    TradeCore.LOGGER.error("Submit Error: API reports failure: {}", message);
                                    future.complete(false);
                                }
                            } else {
                                TradeCore.LOGGER.error("Submit Error: Invalid JSON object received: {}", responseBody);
                                future.complete(false);
                            }
                        } catch (JsonSyntaxException jsonEx) {
                            TradeCore.LOGGER.error("Submit Error: Invalid JSON syntax: {}", responseBody, jsonEx);
                            future.complete(false);
                        }
                    } else {
                        TradeCore.LOGGER.error("Submit Error: HTTP Status {}", response.statusCode());
                        future.complete(false);
                    }
                } catch (Exception e) {
                    if (!(e instanceof InterruptedException)) {
                        TradeCore.LOGGER.error("Submit Error (Network/Exception): ", e);
                    } else {
                        Thread.currentThread().interrupt();
                    }
                    future.complete(false);
                }
            });
        } catch (RejectedExecutionException e) {
            TradeCore.LOGGER.error("Executor is shut down, cannot execute submitPriceSuggestion", e);
            future.complete(false);
        }
        return future;
    }

    public ClaimResult submitClaimRequest(String playerUuid, String minecraftName, String discordId) {
        if (Thread.currentThread().isInterrupted()) {
            return new ClaimResult(false, "Interner Thread wurde unterbrochen.");
        }
        try {
            TradeCore.LOGGER.info("Sende Claim-Anfrage für UUID: {}, Name: {}, Discord ID: {}", playerUuid, minecraftName, discordId);
            JsonObject payload = new JsonObject();
            payload.addProperty("playerUuid", playerUuid);
            payload.addProperty("minecraftName", minecraftName);
            payload.addProperty("discordId", discordId);

            String jsonBody = GSON.toJson(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_CLAIM_DISCORD_URL))
                    .header("Content-Type", "application/json")
                    .header(CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE)
                    .POST(BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String responseBody = response.body();
                if (responseBody == null || responseBody.trim().isEmpty() || responseBody.trim().equalsIgnoreCase("null")) {
                    TradeCore.LOGGER.error("Claim Error: API response empty/null.");
                    return new ClaimResult(false, "Leere oder ungültige Antwort vom Server.");
                }
                try {
                    JsonElement jsonElement = JsonParser.parseString(responseBody);
                    if (jsonElement != null && jsonElement.isJsonObject()) {
                        JsonObject jsonResponse = jsonElement.getAsJsonObject();
                        boolean success = jsonResponse.has("success") && jsonResponse.get("success").getAsBoolean();
                        String message = jsonResponse.has("message") ? jsonResponse.get("message").getAsString() : (success ? "Erfolgreich" : "Unbekannter Fehler");
                        return new ClaimResult(success, message);
                    } else {
                        TradeCore.LOGGER.error("Claim Error: Invalid JSON object received: {}", responseBody);
                        return new ClaimResult(false, "Ungültiges JSON-Format vom Server.");
                    }
                } catch (JsonSyntaxException jsonEx) {
                    TradeCore.LOGGER.error("Claim Error: Invalid JSON syntax: {}", responseBody, jsonEx);
                    return new ClaimResult(false, "Ungültige JSON-Syntax vom Server.");
                }
            } else {
                TradeCore.LOGGER.error("Claim Error: HTTP Status {}. Body: {}", response.statusCode(), response.body());
                return new ClaimResult(false, "Serverfehler: HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                return new ClaimResult(false, "Anfrage unterbrochen.");
            }
            TradeCore.LOGGER.error("Claim Error (Network/Exception): ", e);
            return new ClaimResult(false, "Netzwerk- oder interner Fehler: " + e.getMessage());
        }
    }


    public PriceResult getItemPrices(String itemName) {
        return priceData.get(itemName);
    }

    public long getLastUpdateTimestamp() {
        return lastUpdateTimestamp.get();
    }

    public void triggerPriceUpdate() {
        TradeCore.LOGGER.info("Manuelle Preisaktualisierung ausgelöst.");
        TradeCoreConfig.updateAndSaveLastFetchTimestamp(System.currentTimeMillis());
        fetchAllPricesAsync();
    }

    public void deletePriceFile() {
        try {
            boolean deleted = Files.deleteIfExists(PRICE_FILE_PATH);
            if (deleted)
                TradeCore.LOGGER.info("Preisdatei {} gelöscht.", PRICE_FILE_PATH.getFileName());
            Files.deleteIfExists(PRICE_FILE_TMP_PATH);
        } catch (IOException | SecurityException e) {
            TradeCore.LOGGER.error("Fehler beim Löschen der Preisdatei {}: ", PRICE_FILE_PATH.getFileName(), e);
        }
        priceData.clear();
        lastUpdateTimestamp.set(0);
        TradeCore.LOGGER.info("In-Memory Preisdaten geleert.");
    }

    public static class PriceResult {
        public final int stackpreis;
        public final int dkpreis;
        public final int stueckpreis;

        public PriceResult(int stackpreis, int dkpreis, int stueckpreis) {
            this.stackpreis = stackpreis;
            this.dkpreis = dkpreis;
            this.stueckpreis = stueckpreis;
        }
    }

    public static class ClaimResult {
        public final boolean success;
        public final String message;

        public ClaimResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    public void shutdown() {
        TradeCore.LOGGER.info("PriceAPIClient shutdown() aufgerufen.");
    }

    public static class BlockOfTheDayResult {
        public final String bdtId;
        public final String itemName;
        public final String gewinn;
        public final String message;
        public final boolean found;
        public final int schnellVotes;
        public final int langsamVotes;
        public final boolean hasVoted;
        public final long remainingEditCooldown;

        public BlockOfTheDayResult(String bdtId, String itemName, String gewinn, int schnellVotes, int langsamVotes, boolean hasVoted, long remainingEditCooldown) {
            this.bdtId = bdtId;
            this.itemName = itemName;
            this.gewinn = gewinn;
            this.schnellVotes = schnellVotes;
            this.langsamVotes = langsamVotes;
            this.hasVoted = hasVoted;
            this.remainingEditCooldown = remainingEditCooldown;
            this.message = null;
            this.found = true;
        }

        public BlockOfTheDayResult(String message) {
            this.bdtId = null;
            this.itemName = "Nicht gefunden";
            this.gewinn = "-";
            this.schnellVotes = 0;
            this.langsamVotes = 0;
            this.hasVoted = false;
            this.remainingEditCooldown = 0;
            this.message = message;
            this.found = false;
        }
    }

    public CompletableFuture<BlockOfTheDayResult> fetchBlockOfTheDayAsync() {
        LocalDate today = LocalDate.now();
        if (cachedBdtResult != null && today.equals(bdtCacheDate)) {
            TradeCore.LOGGER.info("Block des Tages aus Cache geladen (Achtung: Votestatus könnte veraltet sein).");
            return CompletableFuture.completedFuture(cachedBdtResult);
        }
        return forceFetchBlockOfTheDayAsync();
    }

    public CompletableFuture<BlockOfTheDayResult> forceFetchBlockOfTheDayAsync() {
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
            TradeCore.LOGGER.warn("Konnte Player UUID für BdT-Abruf nicht ermitteln (Client nicht verfügbar).");
        }
        final String finalPlayerUuid = playerUuid;

        try {
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
                            .GET()
                            .timeout(Duration.ofSeconds(10))
                            .build();

                    HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());
                    BlockOfTheDayResult result;
                    LocalDate todayResponse = LocalDate.now();

                    if (response.statusCode() == 200) {
                        String responseBody = response.body();
                        if (responseBody == null || responseBody.trim().isEmpty() || responseBody.trim().equalsIgnoreCase("null")) {
                            result = new BlockOfTheDayResult("Fehler: Leere Server-Antwort");
                        } else {
                            try {
                                JsonElement jsonElement = JsonParser.parseString(responseBody);
                                if (jsonElement.isJsonObject()) {
                                    JsonObject jsonResponse = jsonElement.getAsJsonObject();
                                    if (jsonResponse.has("success") && jsonResponse.get("success").getAsBoolean()) {
                                        String bdtId = jsonResponse.has("bdt_id") ? jsonResponse.get("bdt_id").getAsString() : null;
                                        String itemName = jsonResponse.has("item_name") ? jsonResponse.get("item_name").getAsString() : "Unbekannt";
                                        String gewinn = jsonResponse.has("gewinn") ? jsonResponse.get("gewinn").getAsString() : "-";
                                        int schnellVotes = jsonResponse.has("schnell_votes") ? jsonResponse.get("schnell_votes").getAsInt() : 0;
                                        int langsamVotes = jsonResponse.has("langsam_votes") ? jsonResponse.get("langsam_votes").getAsInt() : 0;
                                        boolean hasVoted = jsonResponse.has("has_voted") ? jsonResponse.get("has_voted").getAsBoolean() : false;
                                        long remainingEditCooldown = jsonResponse.has("remaining_edit_cooldown") ? jsonResponse.get("remaining_edit_cooldown").getAsLong() : 0;

                                        if (bdtId == null) {
                                            TradeCore.LOGGER.error("BdT von API erhalten (force fetch), aber ohne 'bdt_id'!");
                                            result = new BlockOfTheDayResult("Fehler: Ungültige Serverdaten (fehlende ID)");
                                        } else {
                                            result = new BlockOfTheDayResult(bdtId, itemName, gewinn, schnellVotes, langsamVotes, hasVoted, remainingEditCooldown);
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

                    if (result.found || (result.message != null && !result.message.startsWith("Fehler:"))) {
                        this.cachedBdtResult = result;
                        this.bdtCacheDate = todayResponse;
                    }
                    future.complete(result);
                } catch (Exception e) {
                    if (!(e instanceof InterruptedException)) {
                        TradeCore.LOGGER.error("Fehler beim Abrufen des BdT (force fetch): ", e);
                    } else {
                        Thread.currentThread().interrupt();
                    }
                    future.complete(new BlockOfTheDayResult("Fehler: Netzwerkproblem"));
                }
            });
        } catch (RejectedExecutionException e) {
            TradeCore.LOGGER.error("Executor is shut down, cannot execute forceFetchBlockOfTheDayAsync", e);
            future.complete(new BlockOfTheDayResult("Fehler: Client heruntergefahren"));
        }
        return future;
    }

    public CompletableFuture<Boolean> createBlockOfTheDayAsync(String itemDisplayName, String gewinn, String playerUuid) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (executor.isShutdown() || executor.isTerminated()) {
            TradeCore.LOGGER.warn("createBlockOfTheDayAsync: Executor heruntergefahren.");
            future.complete(false);
            return future;
        }

        try {
            executor.submit(() -> {
                if (Thread.currentThread().isInterrupted()) {
                    future.complete(false);
                    return;
                }
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
                            .timeout(Duration.ofSeconds(10))
                            .build();
                    HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        String responseBody = response.body();
                        if (responseBody == null || responseBody.trim().isEmpty() || responseBody.trim().equalsIgnoreCase("null")) {
                            TradeCore.LOGGER.error("Create BdT Error: API response empty/null.");
                            future.complete(false);
                            return;
                        }
                        try {
                            JsonElement jsonElement = JsonParser.parseString(responseBody);
                            if (jsonElement != null && jsonElement.isJsonObject()) {
                                JsonObject jsonResponse = jsonElement.getAsJsonObject();
                                boolean success = jsonResponse.has("success") && jsonResponse.get("success").getAsBoolean();
                                if (success) {
                                    TradeCore.LOGGER.info("Block des Tages erfolgreich erstellt.");
                                    this.cachedBdtResult = null;
                                    this.bdtCacheDate = null;
                                    future.complete(true);
                                } else {
                                    String message = jsonResponse.has("message") ? jsonResponse.get("message").getAsString() : "Unbekannter API Fehler.";
                                    TradeCore.LOGGER.error("Create BdT Error: API reports failure: {}", message);
                                    future.complete(false);
                                }
                            } else {
                                TradeCore.LOGGER.error("Create BdT Error: Invalid JSON Object");
                                future.complete(false);
                            }
                        } catch (JsonSyntaxException jsonEx) {
                            TradeCore.LOGGER.error("Create BdT Error: Invalid JSON Syntax", jsonEx);
                            future.complete(false);
                        }
                    } else {
                        TradeCore.LOGGER.error("Create BdT Error: HTTP Status {} - Body: {}", response.statusCode(), response.body());
                        future.complete(false);
                    }
                } catch (Exception e) {
                    if (!(e instanceof InterruptedException)) {
                        TradeCore.LOGGER.error("Create BdT Error (Network/Exception): ", e);
                    } else {
                        Thread.currentThread().interrupt();
                    }
                    future.complete(false);
                }
            });
        } catch (RejectedExecutionException e) {
            TradeCore.LOGGER.error("Executor is shut down, cannot execute createBlockOfTheDayAsync", e);
            future.complete(false);
        }
        return future;
    }

    public CompletableFuture<Boolean> submitBdtVoteAsync(String bdtId, String playerUuid, String voteType) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (executor.isShutdown() || executor.isTerminated()) {
            TradeCore.LOGGER.warn("submitBdtVoteAsync: Executor heruntergefahren.");
            future.complete(false);
            return future;
        }
        if (!("schnell".equals(voteType) || "langsam".equals(voteType))) {
            TradeCore.LOGGER.error("Ungültiger voteType: {}", voteType);
            future.complete(false);
            return future;
        }
        if (bdtId == null || bdtId.trim().isEmpty()) {
            TradeCore.LOGGER.error("Ungültige bdtId für Abstimmung.");
            future.complete(false);
            return future;
        }

        try {
            executor.submit(() -> {
                if (Thread.currentThread().isInterrupted()) {
                    future.complete(false);
                    return;
                }
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
                            .timeout(Duration.ofSeconds(10))
                            .build();
                    HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        String responseBody = response.body();
                        if (responseBody == null || responseBody.trim().isEmpty() || responseBody.trim().equalsIgnoreCase("null")) {
                            TradeCore.LOGGER.error("Submit Vote Error: API response empty/null.");
                            future.complete(false);
                            return;
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
                            } else {
                                TradeCore.LOGGER.error("Submit Vote Error: Invalid JSON Object");
                                future.complete(false);
                            }
                        } catch (JsonSyntaxException jsonEx) {
                            TradeCore.LOGGER.error("Submit Vote Error: Invalid JSON Syntax", jsonEx);
                            future.complete(false);
                        }
                    } else {
                        TradeCore.LOGGER.error("Submit Vote Error: HTTP Status {} - Body: {}", response.statusCode(), response.body());
                        future.complete(false);
                    }
                } catch (Exception e) {
                    if (!(e instanceof InterruptedException)) {
                        TradeCore.LOGGER.error("Submit Vote Error (Network/Exception): ", e);
                    } else {
                        Thread.currentThread().interrupt();
                    }
                    future.complete(false);
                }
            });
        } catch (RejectedExecutionException e) {
            TradeCore.LOGGER.error("Executor is shut down, cannot execute submitBdtVoteAsync", e);
            future.complete(false);
        }
        return future;
    }

    public CompletableFuture<UpdateBdtResult> updateBlockOfTheDayAsync(String bdtId, String itemDisplayName, String gewinn) {
        CompletableFuture<UpdateBdtResult> future = new CompletableFuture<>();
        if (executor.isShutdown() || executor.isTerminated()) {
            TradeCore.LOGGER.warn("updateBlockOfTheDayAsync: Executor heruntergefahren.");
            future.complete(new UpdateBdtResult(false, "Fehler: Client heruntergefahren"));
            return future;
        }

        String playerUuid = "";
        MinecraftClient mcClient = MinecraftClient.getInstance();
        if (mcClient != null && mcClient.player != null) {
            playerUuid = mcClient.player.getUuidAsString();
        } else {
            TradeCore.LOGGER.warn("Konnte Player UUID für BdT-Update nicht ermitteln.");
            future.complete(new UpdateBdtResult(false, "Fehler: Spieler nicht gefunden"));
            return future;
        }
        final String finalPlayerUuid = playerUuid;

        try {
            executor.submit(() -> {
                if (Thread.currentThread().isInterrupted()) {
                    future.complete(new UpdateBdtResult(false, "Fehler: Thread unterbrochen"));
                    return;
                }
                try {
                    TradeCore.LOGGER.info("Aktualisiere Block des Tages {}: {} / {}", bdtId, itemDisplayName, gewinn);
                    JsonObject payload = new JsonObject();
                    payload.addProperty("bdtId", bdtId);
                    payload.addProperty("itemDisplayName", itemDisplayName);
                    payload.addProperty("gewinn", gewinn);
                    payload.addProperty("playerUuid", finalPlayerUuid);
                    String jsonBody = GSON.toJson(payload);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(API_UPDATE_BDT_URL))
                            .header("Content-Type", "application/json")
                            .header(CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE)
                            .POST(BodyPublishers.ofString(jsonBody))
                            .timeout(Duration.ofSeconds(10))
                            .build();
                    HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        String responseBody = response.body();
                        if (responseBody == null || responseBody.trim().isEmpty() || responseBody.trim().equalsIgnoreCase("null")) {
                            TradeCore.LOGGER.error("Update BdT Error: API response empty/null.");
                            future.complete(new UpdateBdtResult(false, "Fehler: Leere Server-Antwort"));
                            return;
                        }
                        try {
                            JsonElement jsonElement = JsonParser.parseString(responseBody);
                            if (jsonElement != null && jsonElement.isJsonObject()) {
                                JsonObject jsonResponse = jsonElement.getAsJsonObject();
                                boolean success = jsonResponse.has("success") && jsonResponse.get("success").getAsBoolean();
                                String message = jsonResponse.has("message") ? jsonResponse.get("message").getAsString() : (success ? "Erfolgreich aktualisiert" : "Unbekannter API Fehler");
                                if (success) {
                                    TradeCore.LOGGER.info("Block des Tages erfolgreich aktualisiert.");
                                    this.cachedBdtResult = null;
                                    this.bdtCacheDate = null;
                                    future.complete(new UpdateBdtResult(true, message));
                                } else {
                                    TradeCore.LOGGER.error("Update BdT Error: API reports failure: {}", message);
                                    future.complete(new UpdateBdtResult(false, message));
                                }
                            } else {
                                TradeCore.LOGGER.error("Update BdT Error: Invalid JSON Object: {}", responseBody);
                                future.complete(new UpdateBdtResult(false, "Fehler: Ungültiges JSON-Format"));
                            }
                        } catch (JsonSyntaxException jsonEx) {
                            TradeCore.LOGGER.error("Update BdT Error: Invalid JSON Syntax: {}", responseBody, jsonEx);
                            future.complete(new UpdateBdtResult(false, "Fehler: Ungültige Server-Antwort"));
                        }
                    } else {
                        String message = response.statusCode() == 429 ? "Cooldown aktiv, bitte warte." : "Fehler: Server nicht erreichbar (HTTP " + response.statusCode() + ")";
                        TradeCore.LOGGER.error("Update BdT Error: HTTP Status {} - Body: {}", response.statusCode(), response.body());
                        future.complete(new UpdateBdtResult(false, message));
                    }
                } catch (Exception e) {
                    if (!(e instanceof InterruptedException)) {
                        TradeCore.LOGGER.error("Update BdT Error (Network/Exception): ", e);
                        future.complete(new UpdateBdtResult(false, "Fehler: Netzwerkproblem"));
                    } else {
                        Thread.currentThread().interrupt();
                        future.complete(new UpdateBdtResult(false, "Fehler: Thread unterbrochen"));
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            TradeCore.LOGGER.error("Executor is shut down, cannot execute updateBlockOfTheDayAsync", e);
            future.complete(new UpdateBdtResult(false, "Fehler: Client heruntergefahren"));
        }
        return future;
    }

    public static class UpdateBdtResult {
        public final boolean success;
        public final String message;

        public UpdateBdtResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    // NEU: Innere Klasse für User Level Daten
    public static class UserLevelResult {
        public final boolean success;
        public final String message;
        public final int level;
        public final int currentXp;
        public final int xpForNextLevel;
        public final String playerName; // Optional, falls die API den Namen zurückgibt

        // Konstruktor für Erfolgsfall
        public UserLevelResult(boolean success, String playerName, int level, int currentXp, int xpForNextLevel) {
            this.success = success;
            this.message = null;
            this.playerName = playerName;
            this.level = level;
            this.currentXp = currentXp;
            this.xpForNextLevel = xpForNextLevel;
        }

        // Konstruktor für Fehlerfall
        public UserLevelResult(boolean success, String message) {
            this.success = success;
            this.message = message;
            this.playerName = null;
            this.level = 0;
            this.currentXp = 0;
            this.xpForNextLevel = 0;
        }
    }

    // NEU: Methode zum Abrufen der User Level Daten
    public CompletableFuture<UserLevelResult> fetchUserLevelAsync(String playerUuid) {
        CompletableFuture<UserLevelResult> future = new CompletableFuture<>();
        if (executor.isShutdown() || executor.isTerminated()) {
            TradeCore.LOGGER.warn("fetchUserLevelAsync: Executor heruntergefahren.");
            future.complete(new UserLevelResult(false, "Fehler: Client heruntergefahren"));
            return future;
        }
        if (playerUuid == null || playerUuid.trim().isEmpty()) {
            TradeCore.LOGGER.error("fetchUserLevelAsync: playerUuid ist null oder leer.");
            future.complete(new UserLevelResult(false, "Fehler: Ungültige Spieler-ID"));
            return future;
        }

        try {
            executor.submit(() -> {
                if (Thread.currentThread().isInterrupted()) {
                    future.complete(new UserLevelResult(false, "Fehler: Thread unterbrochen"));
                    return;
                }
                TradeCore.LOGGER.info("Rufe User Level für UUID {} von {} ab...", playerUuid, API_GET_USER_LEVEL_URL);
                try {
                    String urlWithParams = API_GET_USER_LEVEL_URL + "?playerUuid=" + playerUuid;
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(urlWithParams))
                            .header(CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE) // Wichtig für deine API
                            .GET()
                            .timeout(Duration.ofSeconds(10))
                            .build();

                    HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());
                    UserLevelResult result;

                    if (response.statusCode() == 200) {
                        String responseBody = response.body();
                        if (responseBody == null || responseBody.trim().isEmpty() || responseBody.trim().equalsIgnoreCase("null")) {
                            result = new UserLevelResult(false, "Fehler: Leere Server-Antwort für Leveldaten");
                        } else {
                            try {
                                JsonElement jsonElement = JsonParser.parseString(responseBody);
                                if (jsonElement.isJsonObject()) {
                                    JsonObject jsonResponse = jsonElement.getAsJsonObject();
                                    boolean success = jsonResponse.has("success") && jsonResponse.get("success").getAsBoolean();
                                    if (success) {
                                        String playerName = jsonResponse.has("playerName") ? jsonResponse.get("playerName").getAsString() : "Unbekannt";
                                        int level = jsonResponse.has("level") ? jsonResponse.get("level").getAsInt() : 0;
                                        int currentXp = jsonResponse.has("currentXp") ? jsonResponse.get("currentXp").getAsInt() : 0;
                                        int xpForNextLevel = jsonResponse.has("xpForNextLevel") ? jsonResponse.get("xpForNextLevel").getAsInt() : 100; // Default, falls nicht vorhanden

                                        result = new UserLevelResult(true, playerName, level, currentXp, xpForNextLevel);
                                        TradeCore.LOGGER.info("User Level Daten erfolgreich für {} (Level {}) erhalten.", playerName, level);
                                    } else {
                                        String message = jsonResponse.has("message") ? jsonResponse.get("message").getAsString() : "Spieler nicht gefunden oder keine Leveldaten vorhanden.";
                                        result = new UserLevelResult(false, message);
                                        TradeCore.LOGGER.info("Keine User Level Daten: {}", message);
                                    }
                                } else {
                                    TradeCore.LOGGER.error("Fehler beim Parsen der User Level JSON: Kein JSON Objekt. Body: {}", responseBody);
                                    result = new UserLevelResult(false, "Fehler: Ungültiges JSON-Format vom Server");
                                }
                            } catch (JsonSyntaxException | IllegalStateException | ClassCastException jsonEx) {
                                TradeCore.LOGGER.error("Fehler beim Parsen der User Level JSON: {}", responseBody, jsonEx);
                                result = new UserLevelResult(false, "Fehler: Ungültige Server-Antwort (Leveldaten)");
                            }
                        }
                    } else {
                        TradeCore.LOGGER.error("Fehler beim Abrufen der User Level Daten: HTTP Status {}", response.statusCode());
                        result = new UserLevelResult(false, "Fehler: Server nicht erreichbar (HTTP " + response.statusCode() + ")");
                    }
                    future.complete(result);
                } catch (Exception e) {
                    if (!(e instanceof InterruptedException)) {
                        TradeCore.LOGGER.error("Fehler beim Abrufen der User Level Daten: ", e);
                    } else {
                        Thread.currentThread().interrupt();
                    }
                    future.complete(new UserLevelResult(false, "Fehler: Netzwerkproblem beim Abrufen der Leveldaten"));
                }
            });
        } catch (RejectedExecutionException e) {
            TradeCore.LOGGER.error("Executor is shut down, cannot execute fetchUserLevelAsync", e);
            future.complete(new UserLevelResult(false, "Fehler: Client (Leveldaten) heruntergefahren"));
        }
        return future;
    }
}