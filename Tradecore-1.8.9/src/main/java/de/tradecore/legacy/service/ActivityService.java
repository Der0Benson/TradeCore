package de.tradecore.legacy.service;

import com.google.gson.Gson;
import de.tradecore.legacy.config.ApiEndpoints;
import de.tradecore.legacy.util.ApiHttp;
import de.tradecore.legacy.util.ClientTasks;

public final class ActivityService {
    private static final Gson GSON = new Gson();
    private static volatile boolean active;
    private ActivityService() {}

    public static boolean isActive() { return active; }

    public static void refresh(String uuid, final Runnable callback) {
        Payload payload = new Payload();
        payload.playerUuid = uuid;
        payload.minecraftVersion = "1.8.9";
        ApiHttp.post(ApiEndpoints.activityStatus(), GSON.toJson(payload), new ApiHttp.Callback() {
            @Override public void complete(ApiHttp.Response response, Exception error) {
                if (error == null && response.successful() && !VerificationRequired.handle(response)) {
                    try {
                        ApiResult result = GSON.fromJson(response.body, ApiResult.class);
                        if (result != null && result.success) active = result.active;
                    } catch (Exception ignored) {}
                }
                ClientTasks.run(callback);
            }
        });
    }

    private static final class Payload { String playerUuid; String minecraftVersion; }
    private static final class ApiResult { boolean success; boolean active; }
}
