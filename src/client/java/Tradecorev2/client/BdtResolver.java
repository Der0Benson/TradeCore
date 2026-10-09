package Tradecorev2.client;

import Tradecorev2.client.config.ApiEndpointManager;
import Tradecorev2.client.util.ApiDebugLogger;
import Tradecorev2.client.util.ApiResponseMessage;
import Tradecorev2.client.util.BdtHttpClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
public class BdtResolver {
   private static final Logger LOGGER = LoggerFactory.getLogger("TradecoreV2-BdtResolver");
   private static final Gson GSON = (new GsonBuilder()).create();

   public static void resolveAsync(String userInput, Consumer<ResolveResult> onResult) {
      String trimmed = userInput == null ? "" : userInput.trim();
      if (trimmed.isEmpty()) {
         ResolveResult result = new ResolveResult();
         result.success = false;
         result.message = "Eingabe ist leer.";
         dispatchResult(onResult, result);
      } else {
         ResolveRequest payload = new ResolveRequest(trimmed);
         String jsonBody = GSON.toJson(payload);

         try {
            long timestamp = Instant.now().getEpochSecond();
            String signature = HmacSignatureUtil.calculateHmacSha256Signature(jsonBody, timestamp);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(ApiEndpointManager.getBdtResolveUrl())).timeout(BdtHttpClient.REQUEST_TIMEOUT).header("Content-Type", "application/json").header("X-Timestamp", String.valueOf(timestamp)).header("X-Signature", signature).POST(BodyPublishers.ofString(jsonBody)).build();
            LOGGER.info("Resolving BdT input via API: {}", trimmed);
            BdtHttpClient.get().sendAsync(request, BodyHandlers.ofString()).thenAccept((response) -> {
               ApiDebugLogger.log(LOGGER, "BdT resolve response", response);
               if (VerificationRequiredHandler.handleIfRequired(response.statusCode(), response.body())) {
                  ResolveResult fail = new ResolveResult();
                  fail.success = false;
                  fail.message = "Discord-Verifizierung erforderlich.";
                  dispatchResult(onResult, fail);
                  return;
               }
               if (!BdtHttpClient.isSuccessful(response.statusCode())) {
                  ResolveResult fail = new ResolveResult();
                  fail.success = false;
                  fail.message = ApiResponseMessage.extract(response.body(), "BdT-Prüfung wurde vom Server abgelehnt (HTTP " + response.statusCode() + ").");
                  dispatchResult(onResult, fail);
                  return;
               }

               handleResponse(response.body(), onResult);
            }).exceptionally((ex) -> {
               LOGGER.error("Failed to resolve BdT input", ex);
               ResolveResult fail = new ResolveResult();
               fail.success = false;
               fail.message = "BdT-Eingabe konnte nicht geprueft werden.";
               dispatchResult(onResult, fail);
               return null;
            });
         } catch (Exception e) {
            LOGGER.error("Could not create HMAC signature for BdtResolver", e);
            ResolveResult fail = new ResolveResult();
            fail.success = false;
            fail.message = "Signatur fuer BdT-Aufloesung konnte nicht erstellt werden.";
            dispatchResult(onResult, fail);
         }

      }
   }

   private static void handleResponse(String responseBody, Consumer<ResolveResult> onResult) {
      try {
         ResolveResult result = (ResolveResult)GSON.fromJson(responseBody, ResolveResult.class);
         if (result == null) {
            result = new ResolveResult();
            result.success = false;
            result.message = "Ungueltige Antwort von der BdT-API.";
         }

         dispatchResult(onResult, result);
      } catch (Exception e) {
         LOGGER.error("Failed to parse BdT resolve response: {}", responseBody, e);
         ResolveResult fail = new ResolveResult();
         fail.success = false;
         fail.message = "Antwort der BdT-API konnte nicht gelesen werden.";
         dispatchResult(onResult, fail);
      }

   }

   private static void dispatchResult(Consumer<ResolveResult> onResult, ResolveResult result) {
      if (onResult != null) {
         try {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
               client.execute(() -> onResult.accept(result));
            } else {
               onResult.accept(result);
            }
         } catch (Exception e) {
            LOGGER.error("Konnte BdT-Resolve-Callback nicht ausführen", e);
            showToastSafe("Fehler", "Konnte Ergebnis nicht an UI liefern.");
         }

      }
   }

   private static void showToastSafe(String title, String description) {
      try {
         Minecraft client = Minecraft.getInstance();
         if (client != null) {
            client.execute(() -> SystemToast.addOrUpdate(client.gui.toastManager(), SystemToastId.PERIODIC_NOTIFICATION, Component.literal(title), Component.literal(description)));
         }
      } catch (Exception var3) {
      }

   }

   @Environment(EnvType.CLIENT)
   public static class ResolveResult {
      public boolean success;
      public String resolved_name;
      public String resolved_id;
      public String message;
   }

   @Environment(EnvType.CLIENT)
   private static class ResolveRequest {
      String query;

      ResolveRequest(String query) {
         this.query = query;
      }
   }
}
