package de.tradecore.legacy.service;

import com.google.gson.Gson;
import de.tradecore.legacy.config.ApiEndpoints;
import de.tradecore.legacy.model.ActionResult;
import de.tradecore.legacy.util.ApiHttp;
import de.tradecore.legacy.util.ApiMessages;
import de.tradecore.legacy.util.ClientTasks;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

public final class DiscordService {
    private static final Gson GSON = new Gson();
    private DiscordService() {}

    public static void link(String discordId, final Consumer<ActionResult> callback) {
        if (discordId == null || !discordId.matches("\\d{17,20}")) {
            deliver(callback, new ActionResult(false, "Bitte gib eine gültige Discord-ID ein."));
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        Payload payload = new Payload();
        payload.discordId = discordId;
        payload.playerUuid = mc.thePlayer == null ? "" : mc.thePlayer.getUniqueID().toString();
        payload.playerName = mc.thePlayer == null ? "" : mc.thePlayer.getName();
        payload.minecraftVersion = "1.8.9";
        ApiHttp.post(ApiEndpoints.linkDiscord(), GSON.toJson(payload), new ApiHttp.Callback() {
            @Override public void complete(ApiHttp.Response response, Exception error) {
                ActionResult result;
                if (error != null) result = new ActionResult(false, "Die Verknüpfungsanfrage konnte nicht gesendet werden.");
                else if (!response.successful()) result = new ActionResult(false,
                    ApiMessages.extract(response.body, "Die Verknüpfungsanfrage wurde abgelehnt."));
                else {
                    try {
                        ApiResult parsed = GSON.fromJson(response.body, ApiResult.class);
                        result = new ActionResult(parsed != null && parsed.success,
                            parsed == null || parsed.message == null ? "Anfrage gesendet." : parsed.message);
                    } catch (Exception exception) { result = new ActionResult(false, "Die empfangenen Daten waren ungültig."); }
                }
                deliver(callback, result);
            }
        });
    }

    private static void deliver(final Consumer<ActionResult> callback, final ActionResult result) {
        ClientTasks.run(new Runnable() { @Override public void run() { if (callback != null) callback.accept(result); } });
    }

    private static final class Payload { String playerUuid; String discordId; String playerName; String minecraftVersion; }
    private static final class ApiResult { boolean success; String message; }
}
