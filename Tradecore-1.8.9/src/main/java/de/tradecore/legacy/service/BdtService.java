package de.tradecore.legacy.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.tradecore.legacy.config.ApiEndpoints;
import de.tradecore.legacy.config.ConfigManager;
import de.tradecore.legacy.model.ActionResult;
import de.tradecore.legacy.model.BdtData;
import de.tradecore.legacy.util.ApiHttp;
import de.tradecore.legacy.util.ApiMessages;
import de.tradecore.legacy.util.ClientTasks;
import de.tradecore.legacy.util.Notifications;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.URLEncoder;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class BdtService {
    private static final Logger LOGGER = LogManager.getLogger("Tradecore-BdT");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile BdtData current;

    private BdtService() {}

    public static BdtData getCurrent() { return current; }
    public static boolean hasData() { return current != null; }

    public static void markVoted() {
        if (current != null) {
            current.has_voted = true;
            saveCache();
        }
    }

    public static void loadCache() {
        File cache = cacheFile();
        if (!cache.isFile()) return;
        try (FileReader reader = new FileReader(cache)) {
            current = GSON.fromJson(reader, BdtData.class);
        } catch (Exception exception) {
            LOGGER.warn("BdT-Cache konnte nicht gelesen werden", exception);
        }
    }

    private static void saveCache() {
        try (FileWriter writer = new FileWriter(cacheFile())) {
            GSON.toJson(current, writer);
        } catch (Exception exception) {
            LOGGER.warn("BdT-Cache konnte nicht gespeichert werden", exception);
        }
    }

    private static File cacheFile() {
        return ConfigManager.siblingFile("tradecore-1.8.9-bdt-cache.json");
    }

    public static void fetch(final boolean force, final Runnable complete) {
        Minecraft mc = Minecraft.getMinecraft();
        String uuid = mc.thePlayer == null ? "" : mc.thePlayer.getUniqueID().toString();
        try {
            String url = ApiEndpoints.getBdt() + "?playerUuid=" + URLEncoder.encode(uuid, "UTF-8")
                + "&minecraftVersion=1.8.9";
            ApiHttp.get(url, new ApiHttp.Callback() {
                @Override public void complete(ApiHttp.Response response, Exception error) {
                    boolean verificationPrompt = false;
                    if (error != null) {
                        LOGGER.warn("BdT konnte nicht geladen werden", error);
                        Notifications.error("BdT konnte nicht geladen werden. Lokaler Cache wird verwendet.");
                    } else {
                        verificationPrompt = VerificationRequired.handle(response);
                    }
                    if (error == null && !verificationPrompt) {
                        if (response.successful()) parseBdt(response.body);
                        else Notifications.error(ApiMessages.extract(response.body, "Der Block des Tages konnte nicht geladen werden."));
                    }
                    if (!verificationPrompt) ClientTasks.run(complete);
                }
            });
        } catch (Exception exception) {
            Notifications.error("BdT-Anfrage konnte nicht vorbereitet werden.");
            ClientTasks.run(complete);
        }
    }

    private static void parseBdt(String body) {
        try {
            BdtResponse response = GSON.fromJson(body, BdtResponse.class);
            if (response != null && response.success) {
                BdtData data = new BdtData();
                data.bdt_id = response.bdt_id;
                data.item_id = response.item_id;
                data.item_name = response.item_name;
                data.gewinn = response.gewinn;
                data.schnell_votes = response.schnell_votes;
                data.langsam_votes = response.langsam_votes;
                data.has_voted = response.has_voted;
                current = data;
                saveCache();
            } else if (response != null) {
                current = null;
                saveCache();
                Notifications.info(response.message == null ? "Kein BdT verfügbar." : response.message);
            }
        } catch (Exception exception) {
            LOGGER.error("Ungültige BdT-Antwort: {}", body, exception);
            Notifications.error("Die empfangenen BdT-Daten waren ungültig.");
        }
    }

    public static void vote(final String type, final Consumer<ActionResult> callback) {
        if (current == null) { deliver(callback, new ActionResult(false, "Kein BdT geladen.")); return; }
        Minecraft mc = Minecraft.getMinecraft();
        VotePayload payload = new VotePayload();
        payload.bdtId = current.bdt_id;
        payload.playerUuid = mc.thePlayer == null ? "" : mc.thePlayer.getUniqueID().toString();
        payload.voteType = type;
        postAction(ApiEndpoints.voteBdt(), GSON.toJson(payload), callback);
    }

    public static void create(String itemId, String itemName, String reward, final Consumer<ActionResult> callback) {
        Minecraft mc = Minecraft.getMinecraft();
        CreatePayload payload = new CreatePayload();
        payload.itemId = itemId;
        payload.itemDisplayName = itemName;
        payload.gewinn = reward;
        payload.playerUuid = mc.thePlayer == null ? "" : mc.thePlayer.getUniqueID().toString();
        payload.minecraftVersion = "1.8.9";
        postAction(ApiEndpoints.createBdt(), GSON.toJson(payload), callback);
    }

    private static void postAction(String url, String body, final Consumer<ActionResult> callback) {
        ApiHttp.post(url, body, new ApiHttp.Callback() {
            @Override public void complete(ApiHttp.Response response, Exception error) {
                ActionResult result;
                if (error != null) result = new ActionResult(false, "Verbindung fehlgeschlagen. Bitte später erneut versuchen.");
                else if (VerificationRequired.handle(response)) return;
                else if (!response.successful()) result = new ActionResult(false, ApiMessages.extract(response.body, "Die Anfrage wurde abgelehnt."));
                else {
                    try {
                        ActionResponse parsed = GSON.fromJson(response.body, ActionResponse.class);
                        result = new ActionResult(parsed != null && parsed.success,
                            parsed == null || parsed.message == null ? "Anfrage abgeschlossen." : parsed.message);
                    } catch (Exception exception) { result = new ActionResult(false, "Die empfangenen Daten waren ungültig."); }
                }
                deliver(callback, result);
            }
        });
    }

    private static void deliver(final Consumer<ActionResult> callback, final ActionResult result) {
        ClientTasks.run(new Runnable() { @Override public void run() { if (callback != null) callback.accept(result); } });
    }

    private static final class BdtResponse extends BdtData { boolean success; String message; }
    private static final class ActionResponse { boolean success; String message; }
    private static final class VotePayload { int bdtId; String playerUuid; String voteType; }
    private static final class CreatePayload { String itemId; String itemDisplayName; String gewinn; String playerUuid; String minecraftVersion; }
}
