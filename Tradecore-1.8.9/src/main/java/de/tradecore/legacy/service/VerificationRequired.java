package de.tradecore.legacy.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tradecore.legacy.gui.DiscordVerificationScreen;
import de.tradecore.legacy.util.ApiHttp;
import de.tradecore.legacy.util.ApiMessages;
import de.tradecore.legacy.util.ClientTasks;
import java.text.Normalizer;
import java.util.Locale;
import net.minecraft.client.Minecraft;

public final class VerificationRequired {
    private static long lastPrompt;
    private VerificationRequired() {}

    public static boolean handle(final ApiHttp.Response response) {
        if (response == null) return false;
        boolean required = response.status == 401 || response.status == 403;
        String body = response.body == null ? "" : response.body;
        try {
            JsonObject json = new JsonParser().parse(body).getAsJsonObject();
            required = required || bool(json, "requires_verification") || bool(json, "verification_required")
                || (json.has("verified") && !json.get("verified").getAsBoolean());
        } catch (Exception ignored) {}
        String normalized = Normalizer.normalize(body.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "");
        required = required && (normalized.contains("discord") || normalized.contains("verifiz")
            || normalized.contains("verif") || normalized.contains("not linked"));
        if (!required) return false;
        long now = System.currentTimeMillis();
        if (now - lastPrompt < 5000L) return true;
        lastPrompt = now;
        final String message = ApiMessages.extract(body,
            "Dein Minecraft-Konto ist noch nicht mit Discord verknüpft.");
        ClientTasks.run(new Runnable() {
            @Override public void run() {
                Minecraft mc = Minecraft.getMinecraft();
                if (!(mc.currentScreen instanceof DiscordVerificationScreen)) {
                    mc.displayGuiScreen(new DiscordVerificationScreen(null, message));
                }
            }
        });
        return true;
    }

    private static boolean bool(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() && object.get(key).getAsBoolean();
    }
}
