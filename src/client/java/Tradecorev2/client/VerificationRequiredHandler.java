package Tradecorev2.client;

import Tradecorev2.client.gui.DiscordVerificationScreen;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.text.Normalizer;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public final class VerificationRequiredHandler {
   private static final long PROMPT_COOLDOWN_MILLIS = 5_000L;
   private static long lastPromptAt;

   private VerificationRequiredHandler() {
   }

   public static boolean handleIfRequired(int statusCode, String responseBody) {
      VerificationError error = parseError(statusCode, responseBody);
      if (error == null) {
         return false;
      }

      Minecraft client = Minecraft.getInstance();
      if (client == null) {
         return true;
      }
      long now = System.currentTimeMillis();
      if (now - lastPromptAt < PROMPT_COOLDOWN_MILLIS) {
         return true;
      }
      lastPromptAt = now;

      // Das geöffnete BdT-Menü zuerst vollständig schließen. Dadurch kann dessen
      // Callback den Verifizierungsdialog nicht verdecken oder wieder ersetzen.
      client.execute(() -> {
         if (client.gui.screen() instanceof DiscordVerificationScreen) {
            return;
         }
         client.gui.setScreen(null);
         client.execute(() -> {
            if (!(client.gui.screen() instanceof DiscordVerificationScreen)) {
               client.gui.setScreen(new DiscordVerificationScreen(null, error.message()));
            }
         });
      });
      return true;
   }

   private static VerificationError parseError(int statusCode, String responseBody) {
      String message = responseBody == null ? "" : responseBody;
      String code = "";
      boolean explicit = false;
      try {
         JsonObject json = JsonParser.parseString(message).getAsJsonObject();
         message = firstNonBlank(
            stringValue(json, "message"),
            firstNonBlank(stringValue(json, "error"), stringValue(json, "detail"))
         );
         code = firstNonBlank(
            stringValue(json, "error_code"),
            firstNonBlank(stringValue(json, "code"), stringValue(json, "errorCode"))
         );
         explicit = booleanValue(json, "requires_verification")
            || booleanValue(json, "verification_required")
            || json.has("verified") && !json.get("verified").getAsBoolean();
      } catch (Exception ignored) {
      }

      String normalizedCode = normalize(code).replace('-', '_').replace(' ', '_');
      String normalizedMessage = normalize(message);
      boolean recognizedCode = normalizedCode.equals("verification_required")
         || normalizedCode.equals("discord_verification_required")
         || normalizedCode.equals("not_verified")
         || normalizedCode.equals("not_linked")
         || normalizedCode.contains("discord_not_linked")
         || normalizedCode.contains("discord_not_verified");
      boolean mentionsDiscord = normalizedMessage.contains("discord");
      boolean negativeLinkState = normalizedMessage.contains("nicht verknuepft")
         || normalizedMessage.contains("nicht verbunden")
         || normalizedMessage.contains("nicht verifiziert")
         || normalizedMessage.contains("keine verknuepfung")
         || normalizedMessage.contains("noch verknuepfen")
         || normalizedMessage.contains("not linked")
         || normalizedMessage.contains("not connected")
         || normalizedMessage.contains("not verified");
      boolean negativeStatement = normalizedMessage.contains("nicht")
         || normalizedMessage.contains("kein")
         || normalizedMessage.contains("not ")
         || normalizedMessage.contains("no ");
      boolean linkageStatement = normalizedMessage.contains("verknuepf")
         || normalizedMessage.contains("verbund")
         || normalizedMessage.contains("verifizier")
         || normalizedMessage.contains("link")
         || normalizedMessage.contains("connect")
         || normalizedMessage.contains("verif");
      boolean recognizedMessage = normalizedMessage.contains("nicht verifiziert")
         || normalizedMessage.contains("nicht verknuepft")
         || normalizedMessage.contains("discord-verifizierung erforderlich")
         || normalizedMessage.contains("discord verifizierung erforderlich")
         || normalizedMessage.contains("verification required")
         || normalizedMessage.contains("account not linked")
         || mentionsDiscord && (negativeLinkState || negativeStatement && linkageStatement);
      if (!explicit && !recognizedCode && !recognizedMessage) {
         return null;
      }

      String userMessage = message == null || message.isBlank()
         ? "Dein Minecraft-Konto ist noch nicht mit Discord verknüpft."
         : message;
      return new VerificationError(statusCode, userMessage);
   }

   private static String stringValue(JsonObject json, String key) {
      return json.has(key) && !json.get(key).isJsonNull() && json.get(key).isJsonPrimitive()
         ? json.get(key).getAsString()
         : "";
   }

   private static boolean booleanValue(JsonObject json, String key) {
      return json.has(key) && !json.get(key).isJsonNull() && json.get(key).isJsonPrimitive()
         && json.get(key).getAsBoolean();
   }

   private static String firstNonBlank(String first, String second) {
      return first != null && !first.isBlank() ? first : second == null ? "" : second;
   }

   private static String normalize(String value) {
      String lowerCase = value == null ? "" : value.toLowerCase(Locale.ROOT)
         .replace("ß", "ss")
         .replace("ü", "ue")
         .replace("ä", "ae")
         .replace("ö", "oe");
      return Normalizer.normalize(lowerCase, Normalizer.Form.NFD)
         .replaceAll("\\p{M}+", "");
   }

   private record VerificationError(int statusCode, String message) {
   }
}
