package Tradecorev2.client.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class ApiResponseMessage {
   private static final int MAX_MESSAGE_LENGTH = 500;

   private ApiResponseMessage() {
   }

   public static String extract(String responseBody, String fallback) {
      if (responseBody != null && !responseBody.isBlank()) {
         try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            String message = stringValue(json, "message");
            if (message.isBlank()) {
               message = stringValue(json, "error");
            }
            if (!message.isBlank()) {
               String trimmed = message.trim();
               return trimmed.length() <= MAX_MESSAGE_LENGTH ? trimmed : trimmed.substring(0, MAX_MESSAGE_LENGTH);
            }
         } catch (Exception ignored) {
         }
      }
      return fallback;
   }

   private static String stringValue(JsonObject json, String key) {
      return json.has(key) && !json.get(key).isJsonNull() && json.get(key).isJsonPrimitive()
         ? json.get(key).getAsString()
         : "";
   }
}
