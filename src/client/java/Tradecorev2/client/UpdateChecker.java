package Tradecorev2.client;

import Tradecorev2.client.config.ApiEndpointManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class UpdateChecker {
   private static final Logger LOGGER = LoggerFactory.getLogger("TradecoreV2-UpdateChecker");
   private static volatile boolean alreadyChecked = false;
   private static volatile String pendingTitle = null;
   private static volatile String pendingDescription = null;
   private static volatile boolean toastShown = false;
   private static volatile boolean titleShown = false;
   private static volatile boolean updateAvailable = false;
   private static volatile String notifyTitle = null;
   private static volatile String notifyDescription = null;

   private UpdateChecker() {
   }

   public static void checkForUpdatesAsync() {
      if (!alreadyChecked) {
         alreadyChecked = true;
         String apiUrl = ApiEndpointManager.getUpdateCheckUrl();
         if (apiUrl != null && !apiUrl.isBlank()) {
            String currentVersion = (String)FabricLoader.getInstance().getModContainer("tradecorev2").map((container) -> container.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
            LOGGER.info("Update check: starting | url='{}' | installed='{}'", apiUrl, currentVersion);

            try {
               HttpClient client = HttpClient.newHttpClient();
               HttpRequest request = HttpRequest.newBuilder().uri(URI.create(apiUrl)).header("Accept", "application/json, text/plain; q=0.8, */*; q=0.5").GET().build();
               client.sendAsync(request, BodyHandlers.ofString()).thenAcceptAsync((response) -> handleResponse(response, currentVersion)).exceptionally((ex) -> {
                  LOGGER.warn("Update check failed: {}", ex.toString());
                  return null;
               });
            } catch (Exception e) {
               LOGGER.warn("Failed to start update check", e);
            }

         } else {
            LOGGER.info("No update check URL available; skipping update check.");
         }
      }
   }

   private static void handleResponse(HttpResponse<String> response, String currentVersion) {
      if (response != null) {
         int status = response.statusCode();
         String body = (String)response.body();
         int bodyLen = body == null ? 0 : body.length();
         LOGGER.info("Update check: response status={} body_length={}", status, bodyLen);
         if (status >= 200 && status < 300) {
            String latest = null;
            String url = null;
            String message = null;
            String trimmed = body == null ? "" : body.trim();

            try {
               if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                  latest = extractJsonString(trimmed, "latest");
                  if (latest == null) {
                     latest = extractJsonString(trimmed, "version");
                  }

                  url = extractJsonString(trimmed, "url");
                  message = extractJsonString(trimmed, "message");
               } else {
                  latest = trimmed;
               }
            } catch (Exception e) {
               LOGGER.warn("Could not parse update API response", e);
            }

            if (latest != null && !latest.isBlank()) {
               LOGGER.info("Update check: parsed latest='{}'", latest);
               int cmp = compareCompoundVersions(latest, currentVersion);
               if (cmp > 0) {
                  String title = "Neue Version verfügbar";
                  String desc = message != null && !message.isBlank() ? message : "Installiert: " + currentVersion + "  Neu: " + latest;
                  showOrQueueToast(title, desc);
                  updateAvailable = true;
                  notifyTitle = title;
                  notifyDescription = desc;
                  if (url != null && !url.isBlank()) {
                     LOGGER.info("Update available: {} -> {} | {}", new Object[]{currentVersion, latest, url});
                  } else {
                     LOGGER.info("Update available: {} -> {}", currentVersion, latest);
                  }
               } else {
                  LOGGER.info("Mod is up-to-date: {}", currentVersion);
               }

            } else {
               LOGGER.warn("Update API did not provide a 'latest' version");
            }
         } else {
            LOGGER.warn("Update API returned non-2xx status: {}", status);
         }
      }
   }

   private static int compareCompoundVersions(String left, String right) {
      if (left == null && right == null) {
         return 0;
      } else if (left == null) {
         return -1;
      } else if (right == null) {
         return 1;
      } else {
         String[] lParts = left.split("-", 2);
         String[] rParts = right.split("-", 2);
         int c = compareSemver(lParts[0], rParts[0]);
         if (c != 0) {
            return c;
         } else {
            return lParts.length == 2 && rParts.length == 2 ? compareSemver(lParts[1], rParts[1]) : 0;
         }
      }
   }

   private static int compareSemver(String a, String b) {
      if (a == null && b == null) {
         return 0;
      } else if (a == null) {
         return -1;
      } else if (b == null) {
         return 1;
      } else {
         String aa = a.replaceAll("[^0-9.]", "");
         String bb = b.replaceAll("[^0-9.]", "");
         String[] as = aa.split("\\.");
         String[] bs = bb.split("\\.");
         int len = Math.max(as.length, bs.length);

         for(int i = 0; i < len; ++i) {
            int ai = i < as.length && !as[i].isEmpty() ? parseIntSafe(as[i]) : 0;
            int bi = i < bs.length && !bs[i].isEmpty() ? parseIntSafe(bs[i]) : 0;
            if (ai != bi) {
               return Integer.compare(ai, bi);
            }
         }

         return 0;
      }
   }

   private static int parseIntSafe(String s) {
      try {
         return Integer.parseInt(s);
      } catch (Exception var2) {
         return 0;
      }
   }

   private static String extractJsonString(String json, String key) {
      String pattern = "\"" + key + "\"";
      int idx = json.indexOf(pattern);
      if (idx < 0) {
         return null;
      } else {
         int colon = json.indexOf(58, idx + pattern.length());
         if (colon < 0) {
            return null;
         } else {
            int startQuote = json.indexOf(34, colon + 1);
            if (startQuote < 0) {
               return null;
            } else {
               int endQuote = json.indexOf(34, startQuote + 1);
               return endQuote < 0 ? null : json.substring(startQuote + 1, endQuote);
            }
         }
      }
   }

   private static void showOrQueueToast(String title, String description) {
      try {
         Minecraft client = Minecraft.getInstance();
         if (client != null && client.gui.toastManager() != null) {
            client.execute(() -> {
               if (!toastShown) {
                  client.gui.toastManager().addToast(new SystemToast(SystemToastId.PERIODIC_NOTIFICATION, Component.literal(title), Component.literal(description)));
                  toastShown = true;
                  LOGGER.info("Update check: toast shown immediately (menu or in-game)");
               }
            });
            if (!titleShown) {
               pendingTitle = title;
               pendingDescription = description;
               LOGGER.info("Update check: queued red title for in-game display");
            }

            return;
         }
      } catch (Throwable var3) {
      }

      pendingTitle = title;
      pendingDescription = description;
      LOGGER.info("Update check: queued toast for later display");
   }

   public static void tryShowPendingToastIfPossible(Minecraft client) {
      if (client != null) {
         String t = notifyTitle != null ? notifyTitle : pendingTitle;
         String d = notifyDescription != null ? notifyDescription : pendingDescription;
         if (updateAvailable && t != null && d != null) {
            if (!titleShown && client.player != null) {
               client.execute(() -> {
                  if (!titleShown) {
                     try {
                        client.gui.hud.setTimes(10, 80, 20);
                        client.gui.hud.setTitle(Component.literal(t).withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}));
                        client.gui.hud.setSubtitle(Component.literal(d).withStyle(ChatFormatting.RED));
                        titleShown = true;
                        LOGGER.info("Update check: red title shown in-game");
                     } catch (Throwable var6) {
                        try {
                           client.gui.hud.getChat().addClientSystemMessage(Component.literal("[TradeCore] " + t + " – " + d).withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}));
                           titleShown = true;
                           LOGGER.info("Update check: red chat message shown as fallback");
                        } catch (Throwable ex2) {
                           LOGGER.warn("Update check: failed to show title and chat message", ex2);
                        }
                     }

                  }
               });
            }

            if (!toastShown && client.gui.toastManager() != null) {
               client.execute(() -> {
                  if (!toastShown) {
                     client.gui.toastManager().addToast(new SystemToast(SystemToastId.PERIODIC_NOTIFICATION, Component.literal(t), Component.literal(d)));
                     toastShown = true;
                  }
               });
               LOGGER.info("Update check: toast shown via tick (menu or in-game)");
            }

            if (titleShown || toastShown) {
               pendingTitle = null;
               pendingDescription = null;
            }

         }
      }
   }
}
