package Tradecorev2.client;

import Tradecorev2.client.config.ApiEndpointManager;
import Tradecorev2.client.util.ActivityStatusManager;
import Tradecorev2.client.util.ApiDebugLogger;
import com.google.gson.Gson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Instant;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class ActivityStatusChecker {
   private static final Logger LOGGER = LoggerFactory.getLogger("TradecoreV2-ActivityStatus");
   private static final Gson GSON = new Gson();

   private ActivityStatusChecker() {
   }

   public static void refreshStatusAsync(String playerUuid, Runnable onComplete) {
      if (playerUuid != null && !playerUuid.isBlank()) {
         String jsonBody = GSON.toJson(new RequestPayload(playerUuid));

         try {
            long timestamp = Instant.now().getEpochSecond();
            String signature = HmacSignatureUtil.calculateHmacSha256Signature(jsonBody, timestamp);
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(ApiEndpointManager.getActivityStatusUrl())).header("Content-Type", "application/json").header("X-Timestamp", String.valueOf(timestamp)).header("X-Signature", signature).POST(BodyPublishers.ofString(jsonBody)).build();
            httpClient.sendAsync(request, BodyHandlers.ofString()).thenAccept((response) -> {
               ApiDebugLogger.log(LOGGER, "Activity status response", response);
               if (VerificationRequiredHandler.handleIfRequired(response.statusCode(), response.body())) {
                  completeOnClientThread(onComplete);
                  return;
               }
               handleResponse((String)response.body());
               completeOnClientThread(onComplete);
            }).exceptionally((ex) -> {
               LOGGER.error("Failed to fetch activity status", ex);
               completeOnClientThread(onComplete);
               return null;
            });
         } catch (Exception e) {
            LOGGER.error("Could not create HMAC signature for activity status", e);
            completeOnClientThread(onComplete);
         }

      } else {
         completeOnClientThread(onComplete);
      }
   }

   private static void handleResponse(String responseBody) {
      try {
         ApiResponse response = (ApiResponse)GSON.fromJson(responseBody, ApiResponse.class);
         if (response != null && response.success) {
            ActivityStatusManager.setStatus(response.active ? ActivityStatusManager.Status.ACTIVE : ActivityStatusManager.Status.INACTIVE);
         }
      } catch (Exception e) {
         LOGGER.error("Failed to parse activity status response: {}", responseBody, e);
      }

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
            LOGGER.error("Failed to complete activity status callback", e);
         }

      }
   }

   @Environment(EnvType.CLIENT)
   private static class RequestPayload {
      String playerUuid;

      RequestPayload(String playerUuid) {
         this.playerUuid = playerUuid;
      }
   }

   @Environment(EnvType.CLIENT)
   private static class ApiResponse {
      boolean success;
      boolean active;
      String status;
      String message;
   }
}
