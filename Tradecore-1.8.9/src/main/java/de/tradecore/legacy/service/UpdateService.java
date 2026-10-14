package de.tradecore.legacy.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tradecore.legacy.TradecoreMod;
import de.tradecore.legacy.config.ApiEndpoints;
import de.tradecore.legacy.util.ApiHttp;
import de.tradecore.legacy.util.Notifications;

public final class UpdateService {
    private static volatile String pendingMessage;
    private static boolean shown;
    private UpdateService() {}

    public static void checkAsync() {
        ApiHttp.get(ApiEndpoints.latest() + "?minecraftVersion=1.8.9", new ApiHttp.Callback() {
            @Override public void complete(ApiHttp.Response response, Exception error) {
                if (error != null || response == null || !response.successful()) return;
                String latest = response.body == null ? "" : response.body.trim();
                String message = null;
                try {
                    JsonObject json = new JsonParser().parse(latest).getAsJsonObject();
                    latest = json.has("latest") ? json.get("latest").getAsString() : json.get("version").getAsString();
                    if (json.has("message")) message = json.get("message").getAsString();
                } catch (Exception ignored) {}
                if (!latest.isEmpty() && compare(latest, TradecoreMod.VERSION) > 0) {
                    pendingMessage = message == null ? "Neue Version verfügbar: " + latest : message;
                }
            }
        });
    }

    public static void showPending() {
        if (!shown && pendingMessage != null) { shown = true; Notifications.info(pendingMessage); }
    }

    private static int compare(String a, String b) {
        String[] aa = a.replaceAll("[^0-9.]", "").split("\\.");
        String[] bb = b.replaceAll("[^0-9.]", "").split("\\.");
        for (int i = 0; i < Math.max(aa.length, bb.length); i++) {
            int av = i < aa.length && !aa[i].isEmpty() ? parse(aa[i]) : 0;
            int bv = i < bb.length && !bb[i].isEmpty() ? parse(bb[i]) : 0;
            if (av != bv) return av < bv ? -1 : 1;
        }
        return 0;
    }

    private static int parse(String value) { try { return Integer.parseInt(value); } catch (Exception ignored) { return 0; } }
}
