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
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class BdtCreator {
   private static final Logger LOGGER = LoggerFactory.getLogger("TradecoreV2-BdtCreator");
   private static final Gson GSON = new Gson();

   private static String getApiUrl() {
      return ApiEndpointManager.getBdtCreateUrl();
   }

   public static void createBdtAsync(String itemId, String itemDisplayName, String gewinn, Consumer<BdtActionResult> onComplete) {
      Minecraft client = Minecraft.getInstance();
      if (client.player == null) {
         LOGGER.error("Cannot create BdT, player is null.");
         complete(client, onComplete, new BdtActionResult(false, "Spielerdaten nicht verfügbar."));
      } else {
         String playerUuid = client.player.getUUID().toString();
         CreateBdtPayload payload = new CreateBdtPayload(itemId, itemDisplayName, gewinn, playerUuid);
         String jsonBody = GSON.toJson(payload);

         try {
            long timestamp = Instant.now().getEpochSecond();
            String signature = HmacSignatureUtil.calculateHmacSha256Signature(jsonBody, timestamp);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(getApiUrl())).timeout(BdtHttpClient.REQUEST_TIMEOUT).header("Content-Type", "application/json").header("X-Timestamp", String.valueOf(timestamp)).header("X-Signature", signature).POST(BodyPublishers.ofString(jsonBody)).build();
            LOGGER.info("Submitting new BdT creation request for {}", itemId);
            BdtHttpClient.get().sendAsync(request, BodyHandlers.ofString()).thenAccept((response) -> {
               ApiDebugLogger.log(LOGGER, "BdT create response", response);
               if (VerificationRequiredHandler.handleIfRequired(response.statusCode(), response.body())) {
                  complete(client, onComplete, new BdtActionResult(false, "Discord-Verifizierung erforderlich."));
                  return;
               }
               if (!BdtHttpClient.isSuccessful(response.statusCode())) {
                  String message = ApiResponseMessage.extract(response.body(), "Erstellung wurde vom Server abgelehnt (HTTP " + response.statusCode() + ").");
                  complete(client, onComplete, new BdtActionResult(false, message));
                  return;
               }

               complete(client, onComplete, handleResponse(response.body()));
            }).exceptionally((ex) -> {
               LOGGER.error("Failed to create BdT via API", ex);
               complete(client, onComplete, new BdtActionResult(false, "BdT konnte nicht erstellt werden."));
               return null;
            });
         } catch (Exception e) {
            LOGGER.error("Could not create HMAC signature for BdtCreator", e);
            complete(client, onComplete, new BdtActionResult(false, "Signatur für BdT konnte nicht erstellt werden."));
         }

      }
   }

   private static BdtActionResult handleResponse(String responseBody) {
      try {
         ApiResponse response = (ApiResponse)GSON.fromJson(responseBody, ApiResponse.class);
         if (response != null && response.success) {
            LOGGER.info("BdT Creator API Success: {}", response.message);
            return new BdtActionResult(true, response.message == null ? "BdT wurde erstellt." : response.message);
         } else if (response != null) {
            LOGGER.error("BdT Creator API Error: {}", response.message);
            return new BdtActionResult(false, response.message == null ? "BdT konnte nicht erstellt werden." : response.message);
         }
      } catch (Exception e) {
         LOGGER.error("Failed to parse BdT Creator API response: {}", responseBody, e);
      }

      return new BdtActionResult(false, "Ungültige Antwort von der BdT-Erstellungs-API erhalten.");
   }

   private static void complete(Minecraft client, Consumer<BdtActionResult> onComplete, BdtActionResult result) {
      client.execute(() -> {
         String title = result.success() ? "BdT erstellt" : "Fehler beim Erstellen";
         SystemToast.addOrUpdate(client.gui.toastManager(), SystemToastId.PERIODIC_NOTIFICATION, Component.literal(title), Component.literal(result.message()));
         if (onComplete != null) {
            onComplete.accept(result);
         }
      });
   }

   @Environment(EnvType.CLIENT)
   private static class CreateBdtPayload {
      String itemId;
      String itemDisplayName;
      String gewinn;
      String playerUuid;

      CreateBdtPayload(String itemId, String itemDisplayName, String gewinn, String playerUuid) {
         this.itemId = itemId;
         this.itemDisplayName = itemDisplayName;
         this.gewinn = gewinn;
         this.playerUuid = playerUuid;
      }
   }

   @Environment(EnvType.CLIENT)
   private static class ApiResponse {
      boolean success;
      String message;
   }
}
