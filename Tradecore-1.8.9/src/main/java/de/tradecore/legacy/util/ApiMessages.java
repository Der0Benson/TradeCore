package de.tradecore.legacy.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class ApiMessages {
    private ApiMessages() {}

    public static String extract(String body, String fallback) {
        if (body == null || body.trim().isEmpty()) return fallback;
        try {
            JsonObject json = new JsonParser().parse(body).getAsJsonObject();
            String value = value(json, "message");
            if (value.isEmpty()) value = value(json, "error");
            if (!value.isEmpty()) return value.length() > 500 ? value.substring(0, 500) : value;
        } catch (Exception ignored) {}
        return fallback;
    }

    private static String value(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString().trim() : "";
    }
}
