package Tradecorev2.client;

import Tradecorev2.client.config.ApiEndpointManager;
import Tradecorev2.client.util.ApiDebugLogger;
import Tradecorev2.client.util.ApiResponseMessage;
import Tradecorev2.client.util.BdtHttpClient;
import com.google.gson.Gson;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Instant;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class DiscordLinker {
   private static final Logger LOGGER = LoggerFactory.getLogger("TradecoreV2-DiscordLinker");

   private static String getApiUrl() {
      return ApiEndpointManager.getDiscordLinkUrl();
   }

   public static void sendLinkRequestAsync(String discordId, Consumer<BdtActionResult> onComplete) {
      Minecraft client = Minecraft.getInstance();
      if (client.player == null) {
         LOGGER.error("Cannot send link request, player is null.");
         complete(client, onComplete, new BdtActionResult(false, "Spielerdaten sind nicht verfügbar."));
      } else if (!discordId.matches("\\d{17,20}")) {
         complete(client, onComplete, new BdtActionResult(false, "Bitte gib eine gültige Discord-ID ein."));
      } else {
         String playerUuid = client.player.getUUID().toString();
         String playerName = client.player.getName().getString();
         LinkPayload payload = new LinkPayload(playerUuid, discordId, playerName);
         String jsonBody = (new Gson()).toJson(payload);
         try {
            long timestamp = Instant.now().getEpochSecond();
            String signature = HmacSignatureUtil.calculateHmacSha256Signature(jsonBody, timestamp);
            HttpRequest request = HttpRequest.newBuilder()
               .uri(URI.create(getApiUrl()))
               .timeout(BdtHttpClient.REQUEST_TIMEOUT)
               .header("Content-Type", "application/json")
               .header("X-Timestamp", String.valueOf(timestamp))
               .header("X-Signature", signature)
               .POST(BodyPublishers.ofString(jsonBody))
               .build();
            LOGGER.info("Sending Discord link request for player {}", playerUuid);
            BdtHttpClient.get().sendAsync(request, BodyHandlers.ofString()).thenAccept((response) -> {
               ApiDebugLogger.log(LOGGER, "Discord link response", response);
               if (!BdtHttpClient.isSuccessful(response.statusCode())) {
                  String message = ApiResponseMessage.extract(response.body(), "Verknüpfungsserver antwortet mit HTTP " + response.statusCode() + ".");
                  complete(client, onComplete, new BdtActionResult(false, message));
                  return;
               }
               complete(client, onComplete, handleResponse(response.body()));
            }).exceptionally((exception) -> {
               LOGGER.error("Failed to send link request to API", exception);
               complete(client, onComplete, new BdtActionResult(false, "Verknüpfungsanfrage konnte nicht gesendet werden."));
               return null;
            });
         } catch (Exception exception) {
            LOGGER.error("Could not sign Discord link request", exception);
            complete(client, onComplete, new BdtActionResult(false, "Verknüpfungsanfrage konnte nicht signiert werden."));
         }
      }
   }

   private static BdtActionResult handleResponse(String responseBody) {
      try {
         ApiResponse response = (ApiResponse)(new Gson()).fromJson(responseBody, ApiResponse.class);
         if (response != null && response.success) {
            LOGGER.info("API Success: {}", response.message);
            return new BdtActionResult(true, response.message == null ? "Anfrage gesendet. Bitte prüfe deine Discord-DMs." : response.message);
         } else if (response != null) {
            LOGGER.error("API Error: {}", response.message);
            return new BdtActionResult(false, response.message == null ? "Verknüpfung wurde abgelehnt." : response.message);
         }
      } catch (Exception e) {
         LOGGER.error("Failed to parse API response: {}", responseBody, e);
      }

      return new BdtActionResult(false, "Ungültige Antwort von der API erhalten.");
   }

   private static void complete(Minecraft client, Consumer<BdtActionResult> onComplete, BdtActionResult result) {
      if (client == null || onComplete == null) {
         return;
      }
      client.execute(() -> onComplete.accept(result));
   }

   @Environment(EnvType.CLIENT)
   private static class LinkPayload {
      String playerUuid;
      String discordId;
      String playerName;

      LinkPayload(String playerUuid, String discordId, String playerName) {
         this.playerUuid = playerUuid;
         this.discordId = discordId;
         this.playerName = playerName;
      }
   }

   @Environment(EnvType.CLIENT)
   private static class ApiResponse {
      boolean success;
      String message;
   }
}
