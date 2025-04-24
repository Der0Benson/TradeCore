package de.tradecore.tradecore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest; // Wichtig für Header-Nutzung
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration; // Für Timeout
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class PriceAPIClient {
    // URL zum Endpunkt, der ALLE Preise liefert (stelle sicher, dass diese korrekt ist)
    private static final String API_ALL_PRICES_URL = "https://mc-tradecore.de/API/get_price.php";

    // Pfad zur lokalen JSON-Datei für Preise
    private static final Path PRICE_FILE_PATH = FabricLoader.getInstance().getConfigDir().resolve(TradeCore.MOD_ID + "_prices.json");
    private static final Path PRICE_FILE_TMP_PATH = FabricLoader.getInstance().getConfigDir().resolve(TradeCore.MOD_ID + "_prices.tmp");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // NEU: Custom Header Konstanten
    private static final String CUSTOM_HEADER_NAME = "X-TradeCore-Client";
    private static final String CUSTOM_HEADER_VALUE = "alkj789-GhJkL-MnOpQ"; // Der spezifische Wert

    private final HttpClient client;
    private final ConcurrentHashMap<String, PriceResult> priceData;
    private final AtomicLong lastUpdateTimestamp = new AtomicLong(0);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public PriceAPIClient() {
        this.client = HttpClient.newHttpClient();
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
                    } catch (IOException e) {
                        lastUpdateTimestamp.set(Instant.now().getEpochSecond());
                        TradeCore.LOGGER.warn("Konnte Zeitstempel der Preisdatei nicht lesen, nutze aktuellen Zeitstempel.");
                    }
                } else {
                    TradeCore.LOGGER.warn("Preisdatei {} ist leer oder enthält ungültige Daten.", PRICE_FILE_PATH.getFileName());
                    lastUpdateTimestamp.set(0);
                }
            } catch (Exception e) {
                TradeCore.LOGGER.error("Fehler beim Laden der Preisdatei {}: ", PRICE_FILE_PATH.getFileName(), e);
                priceData.clear();
                lastUpdateTimestamp.set(0);
                try {
                    Files.deleteIfExists(PRICE_FILE_PATH);
                    TradeCore.LOGGER.info("Fehlerhafte Preisdatei {} gelöscht.", PRICE_FILE_PATH.getFileName());
                } catch (IOException ex) {
                    TradeCore.LOGGER.error("Konnte fehlerhafte Preisdatei {} nicht löschen.", PRICE_FILE_PATH.getFileName(), ex);
                }
            }
        } else {
            TradeCore.LOGGER.info("Keine lokale Preisdatei {} gefunden.", PRICE_FILE_PATH.getFileName());
            lastUpdateTimestamp.set(0);
        }
    }

    // Speichert die aktuellen In-Memory-Preise in die lokale Datei
    private void savePricesToDisk() {
        Map<String, PriceResult> pricesToSave = Map.copyOf(priceData);

        try (BufferedWriter writer = Files.newBufferedWriter(PRICE_FILE_TMP_PATH)) {
            GSON.toJson(pricesToSave, writer);
            Files.move(PRICE_FILE_TMP_PATH, PRICE_FILE_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            long now = Instant.now().getEpochSecond();
            lastUpdateTimestamp.set(now);
            TradeCore.LOGGER.info("Preisdaten ({}) erfolgreich in {} gespeichert.", pricesToSave.size(), PRICE_FILE_PATH.getFileName());
        } catch (Exception e) {
            TradeCore.LOGGER.error("Fehler beim Speichern der Preisdatei {}: ", PRICE_FILE_PATH.getFileName(), e);
            try {
                Files.deleteIfExists(PRICE_FILE_TMP_PATH);
            } catch (IOException ex) {
                // Ignore cleanup error
            }
        }
    }

    // Startet das Abrufen aller Preise asynchron mit dem Custom Header
    public void fetchAllPricesAsync() {
        executor.submit(() -> {
            TradeCore.LOGGER.info("Starte asynchrones Abrufen aller Preise von {}...", API_ALL_PRICES_URL);
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_ALL_PRICES_URL))
                        // Füge den spezifischen Custom Header hinzu
                        .header(CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE)
                        .GET()
                        .timeout(Duration.ofSeconds(15))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (jsonResponse.has("success") && jsonResponse.get("success").getAsBoolean() && jsonResponse.has("prices")) {
                        JsonObject pricesJson = jsonResponse.getAsJsonObject("prices");
                        Map<String, PriceResult> fetchedPrices = new ConcurrentHashMap<>();
                        Type priceResultType = new TypeToken<PriceResult>() {}.getType();

                        for (Map.Entry<String, com.google.gson.JsonElement> entry : pricesJson.entrySet()) {
                            try {
                                PriceResult priceResult = GSON.fromJson(entry.getValue(), priceResultType);
                                if (priceResult != null) {
                                    fetchedPrices.put(entry.getKey(), priceResult);
                                }
                            } catch (Exception e) {
                                TradeCore.LOGGER.warn("Fehler beim Parsen des Preises für Item '{}': {}", entry.getKey(), e.getMessage());
                            }
                        }

                        if (!fetchedPrices.isEmpty()) {
                            priceData.clear();
                            priceData.putAll(fetchedPrices);
                            TradeCore.LOGGER.info("Erfolgreich {} Preise von der API erhalten.", priceData.size());
                            savePricesToDisk();
                        } else {
                            TradeCore.LOGGER.warn("API hat keine gültigen Preisdaten zurückgegeben (leeres 'prices'-Objekt?).");
                        }

                    } else {
                        String reason = jsonResponse.has("message") ? jsonResponse.get("message").getAsString() : "Keine Erfolgsmeldung oder 'prices'-Feld im JSON.";
                        TradeCore.LOGGER.error("API-Anfrage nicht erfolgreich: {}", reason);
                    }
                } else {
                    // Hier könnten wir spezifischer auf 403 Forbidden (wegen falschem Header) reagieren
                    if (response.statusCode() == 403) {
                        TradeCore.LOGGER.error("API-Anfrage fehlgeschlagen: Status 403 (Forbidden). Prüfe den Custom Header ('{}')!", CUSTOM_HEADER_NAME);
                    } else {
                        TradeCore.LOGGER.error("API-Anfrage fehlgeschlagen: Status {}", response.statusCode());
                    }
                }
            } catch (Exception e) {
                TradeCore.LOGGER.error("Fehler beim Abrufen aller Preise von der API: ", e);
            }
        });
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

        // Gson benötigt diesen Konstruktor nicht zwingend, wenn Felder public sind,
        // aber er schadet nicht und ist nützlich für manuelle Instanziierung.
        public PriceResult(int stackpreis, int dkpreis) {
            this.stackpreis = stackpreis;
            this.dkpreis = dkpreis;
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