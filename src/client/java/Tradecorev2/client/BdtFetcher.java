package Tradecorev2.client;

import Tradecorev2.client.config.ApiEndpointManager;
import Tradecorev2.client.util.ApiDebugLogger;
import Tradecorev2.client.util.ApiResponseMessage;
import Tradecorev2.client.util.BdtHttpClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class BdtFetcher {
   private static final Logger LOGGER = LoggerFactory.getLogger("TradecoreV2-BdtFetcher");
   private static final Gson GSON = (new GsonBuilder()).create();

   private static String getApiUrl() {
      return ApiEndpointManager.getBdtGetUrl();
   }

   public static void fetchBdtFromApiAsync(String playerUuid, Runnable onComplete) {
      fetchBdtFromApiAsync(playerUuid, onComplete, false);
   }

   public static void fetchBdtFromApiAsync(String playerUuid, Runnable onComplete, boolean forceRefresh) {
      if (!isInternetAvailable()) {
         LOGGER.warn("Keine Internetverbindung verfügbar");
         showToast(Minecraft.getInstance(), "Hinweis", "Keine Internetverbindung. Verwende lokale BDT-Daten.");
         if (BdtCache.hasBdtData()) {
            BdtData localData = BdtCache.getBdtData();
            LOGGER.info("BDT erfolgreich aus lokalen Daten geladen (offline): {}", localData.item_name);
            completeOnClientThread(onComplete);
         } else {
            showToast(Minecraft.getInstance(), "Fehler", "Keine lokalen BDT-Daten verfuegbar und keine Internetverbindung.");
            completeOnClientThread(onComplete);
         }
      } else if (!forceRefresh && BdtCache.hasBdtData()) {
         BdtData localData = BdtCache.getBdtData();
         LOGGER.info("Verwende Cache-BDT-Daten: {}, hole aktuelle Abstimmungsdaten", localData.item_name);
         fetchVotingDataOnly(playerUuid, localData, onComplete);
      } else {
         String fullUrl = getApiUrl();
         if (playerUuid != null && !playerUuid.isEmpty()) {
            try {
               fullUrl = fullUrl + "?playerUuid=" + URLEncoder.encode(playerUuid, StandardCharsets.UTF_8.toString());
            } catch (Exception e) {
               LOGGER.error("Failed to encode player UUID for Bdt API request", e);
               showToast(Minecraft.getInstance(), "Fehler", "UUID konnte nicht kodiert werden.");
               completeOnClientThread(onComplete);
               return;
            }
         }

         try {
            long timestamp = Instant.now().getEpochSecond();
            String signature = HmacSignatureUtil.calculateHmacSha256Signature("", timestamp);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(fullUrl)).timeout(BdtHttpClient.REQUEST_TIMEOUT).header("X-Timestamp", String.valueOf(timestamp)).header("X-Signature", signature).GET().build();
            LOGGER.info("Requesting Block des Tages from API: {}", fullUrl);
            BdtHttpClient.get().sendAsync(request, BodyHandlers.ofString()).thenAccept((response) -> {
               ApiDebugLogger.log(LOGGER, "BdT fetch response", response);
               if (VerificationRequiredHandler.handleIfRequired(response.statusCode(), response.body())) {
                  completeOnClientThread(onComplete);
                  return;
               }
               if (!BdtHttpClient.isSuccessful(response.statusCode())) {
                  LOGGER.error("BdT API returned HTTP {}", response.statusCode());
                  String message = ApiResponseMessage.extract(response.body(), "BdT-Server antwortet mit HTTP " + response.statusCode() + ".");
                  showToast(Minecraft.getInstance(), "Fehler", message);
                  completeOnClientThread(onComplete);
                  return;
               }

               parseAndCacheResponse(response.body(), onComplete);
            }).exceptionally((ex) -> {
               LOGGER.error("Failed to fetch Block des Tages from API", ex);
               showToast(Minecraft.getInstance(), "Fehler", "BdT konnte nicht abgerufen werden. Siehe Logs.");
               completeOnClientThread(onComplete);
               return null;
            });
         } catch (Exception e) {
            LOGGER.error("Could not create HMAC signature for BdtFetcher", e);
            showToast(Minecraft.getInstance(), "Fehler", "Signatur fuer BdtFetcher konnte nicht erstellt werden.");
            completeOnClientThread(onComplete);
         }

      }
   }

   private static void parseAndCacheResponse(String responseBody, Runnable onComplete) {
      try {
         ApiResponse apiResponse = (ApiResponse)GSON.fromJson(responseBody, ApiResponse.class);
         if (apiResponse != null && apiResponse.success) {
            BdtData existingData = BdtCache.getBdtData();
            BdtData bdtData;
            if (existingData != null) {
               if (existingData.bdt_id == apiResponse.bdt_id) {
                  bdtData = existingData;
                  if (apiResponse.item_id != null && !apiResponse.item_id.isBlank()) {
                     existingData.item_id = apiResponse.item_id;
                  }
                  if (apiResponse.item_name != null && !apiResponse.item_name.isBlank()) {
                     existingData.item_name = apiResponse.item_name;
                  }
                  existingData.schnell_votes = apiResponse.schnell_votes;
                  existingData.langsam_votes = apiResponse.langsam_votes;
                  existingData.has_voted = apiResponse.has_voted;
                  LOGGER.info("Lokale BDT-Daten beibehalten, Abstimmungsdaten aktualisiert: {} (schnell: {}, langsam: {})", new Object[]{existingData.item_name, existingData.schnell_votes, existingData.langsam_votes});
               } else {
                  bdtData = mapApiResponseToBdtData(apiResponse);
                  LOGGER.warn("Lokale BdT-ID ({}) stimmt nicht mit API-ID ({}) überein. Cache wird ersetzt.", existingData.bdt_id, apiResponse.bdt_id);
               }
            } else {
               bdtData = mapApiResponseToBdtData(apiResponse);
               LOGGER.info("BDT erfolgreich von der API abgerufen: {}", bdtData.item_name);
            }

            BdtCache.setBdtData(bdtData);
         } else if (apiResponse != null && !apiResponse.success) {
            LOGGER.warn("API returned unsuccessful response for BdT: {}", apiResponse.message);
            BdtCache.setBdtData((BdtData)null);
            showToast(Minecraft.getInstance(), "Hinweis", apiResponse.message);
         } else {
            LOGGER.error("Failed to parse Bdt API response: {}", responseBody);
            showToast(Minecraft.getInstance(), "Fehler", "Ungültige BdT-Antwort erhalten.");
         }
      } catch (Exception e) {
         LOGGER.error("Failed to parse Bdt API response.", e);
         showToast(Minecraft.getInstance(), "Fehler", "Fehler beim Parsen der BdT-Daten.");
      } finally {
         completeOnClientThread(onComplete);
      }

   }

   public static boolean shouldRefreshLocalData() {
      boolean has = BdtCache.hasBdtData();
      if (!has) {
         LOGGER.info("Keine BDT-Daten im Cache vorhanden, API-Abruf erforderlich");
      }

      return !has;
   }

   private static void fetchVotingDataOnly(String playerUuid, BdtData localData, Runnable onComplete) {
      String fullUrl = getApiUrl();
      if (playerUuid != null && !playerUuid.isEmpty()) {
         try {
            fullUrl = fullUrl + "?playerUuid=" + URLEncoder.encode(playerUuid, StandardCharsets.UTF_8.toString());
         } catch (Exception e) {
            LOGGER.error("Failed to encode player UUID for voting data request", e);
            showToast(Minecraft.getInstance(), "Fehler", "UUID konnte nicht kodiert werden.");
            completeOnClientThread(onComplete);
            return;
         }
      }

      try {
         long timestamp = Instant.now().getEpochSecond();
         String signature = HmacSignatureUtil.calculateHmacSha256Signature("", timestamp);
         HttpRequest request = HttpRequest.newBuilder().uri(URI.create(fullUrl)).timeout(BdtHttpClient.REQUEST_TIMEOUT).header("X-Timestamp", String.valueOf(timestamp)).header("X-Signature", signature).GET().build();
         LOGGER.info("Requesting voting data from API: {}", fullUrl);
         BdtHttpClient.get().sendAsync(request, BodyHandlers.ofString()).thenAccept((response) -> {
            ApiDebugLogger.log(LOGGER, "BdT voting data response", response);
            if (VerificationRequiredHandler.handleIfRequired(response.statusCode(), response.body())) {
               completeOnClientThread(onComplete);
               return;
            }
            if (!BdtHttpClient.isSuccessful(response.statusCode())) {
               LOGGER.error("BdT voting API returned HTTP {}", response.statusCode());
               String message = ApiResponseMessage.extract(response.body(), "Abstimmungsdaten konnten nicht aktualisiert werden (HTTP " + response.statusCode() + ").");
               showToast(Minecraft.getInstance(), "Hinweis", message);
               completeOnClientThread(onComplete);
               return;
            }

            parseAndUpdateVotingData(response.body(), localData, onComplete);
         }).exceptionally((ex) -> {
            LOGGER.error("Failed to fetch voting data from API", ex);
            showToast(Minecraft.getInstance(), "Hinweis", "Abstimmungsdaten konnten nicht abgerufen werden. Verwende lokale Daten.");
            completeOnClientThread(onComplete);
            return null;
         });
      } catch (Exception e) {
         LOGGER.error("Could not create HMAC signature for voting data request", e);
         showToast(Minecraft.getInstance(), "Fehler", "Signatur fuer Abstimmungsdaten konnte nicht erstellt werden.");
         completeOnClientThread(onComplete);
      }

   }

   private static void parseAndUpdateVotingData(String responseBody, BdtData localData, Runnable onComplete) {
      try {
         ApiResponse apiResponse = (ApiResponse)GSON.fromJson(responseBody, ApiResponse.class);
         if (apiResponse != null && apiResponse.success) {
            if (localData != null && localData.bdt_id == apiResponse.bdt_id) {
               localData.schnell_votes = apiResponse.schnell_votes;
               localData.langsam_votes = apiResponse.langsam_votes;
               localData.has_voted = apiResponse.has_voted;
               BdtCache.setBdtData(localData);
               LOGGER.info("Abstimmungsdaten erfolgreich aktualisiert fuer: {} (schnell: {}, langsam: {})", new Object[]{localData.item_name, localData.schnell_votes, localData.langsam_votes});
            } else {
               BdtData replacement = mapApiResponseToBdtData(apiResponse);
               BdtCache.setBdtData(replacement);
               LOGGER.warn("Abstimmungsdaten gehoeren zu anderem BdT (lokal: {}, API: {}). Cache ersetzt mit neuem Datensatz.", localData != null ? localData.bdt_id : -1, apiResponse.bdt_id);
            }
         } else if (apiResponse != null && !apiResponse.success) {
            LOGGER.warn("API returned unsuccessful response for voting data: {}", apiResponse.message);
            showToast(Minecraft.getInstance(), "Hinweis", "Abstimmungsdaten konnten nicht abgerufen werden: " + apiResponse.message);
         } else {
            LOGGER.error("Failed to parse voting data API response: {}", responseBody);
            showToast(Minecraft.getInstance(), "Fehler", "Ungueltige Abstimmungsdaten-Antwort erhalten.");
         }
      } catch (Exception e) {
         LOGGER.error("Failed to parse voting data API response.", e);
         showToast(Minecraft.getInstance(), "Fehler", "Fehler beim Parsen der Abstimmungsdaten.");
      } finally {
         completeOnClientThread(onComplete);
      }

   }

   private static boolean isInternetAvailable() {
      return true;
   }

   private static BdtData mapApiResponseToBdtData(ApiResponse apiResponse) {
      BdtData bdtData = new BdtData();
      bdtData.bdt_id = apiResponse.bdt_id;
      bdtData.item_id = apiResponse.item_id;
      bdtData.item_name = apiResponse.item_name;
      bdtData.gewinn = apiResponse.gewinn;
      bdtData.schnell_votes = apiResponse.schnell_votes;
      bdtData.langsam_votes = apiResponse.langsam_votes;
      bdtData.has_voted = apiResponse.has_voted;
      return bdtData;
   }

   private static void completeOnClientThread(Runnable onComplete) {
      if (onComplete != null) {
         try {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
               client.execute(onComplete);
            } else {
               onComplete.run();
            }
         } catch (Exception e) {
            LOGGER.error("Konnte Abschlussaktion nicht auf dem Client-Thread ausführen", e);
         }

      }
   }

   private static void showToast(Minecraft client, String title, String description) {
      client.execute(() -> SystemToast.addOrUpdate(client.gui.toastManager(), SystemToastId.PERIODIC_NOTIFICATION, Component.literal(title), Component.literal(description)));
   }

   @Environment(EnvType.CLIENT)
   private static class ApiResponse {
      boolean success;
      int bdt_id;
      String item_id;
      String item_name;
      String gewinn;
      int schnell_votes;
      int langsam_votes;
      boolean has_voted;
      String message;
   }
}
