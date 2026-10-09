package Tradecorev2.client.config;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ApiEndpointManager {
   private static final String BASE_URL = "https://mc-tradecore.de/API";

   private ApiEndpointManager() {
   }

   public static String getUpdateCheckUrl() {
      return BASE_URL + "/latest.php";
   }

   public static String getBdtCreateUrl() {
      return BASE_URL + "/create_bdt.php";
   }

   public static String getBdtGetUrl() {
      return BASE_URL + "/get_bdt.php";
   }

   public static String getBdtVoteUrl() {
      return BASE_URL + "/submit_bdt_vote.php";
   }

   public static String getBdtResolveUrl() {
      return BASE_URL + "/resolve_bdt.php";
   }

   public static String getDiscordLinkUrl() {
      return BASE_URL + "/link_discord.php";
   }

   public static String getActivityStatusUrl() {
      return BASE_URL + "/activity_status.php";
   }

   public static String getBaseUrl() {
      return BASE_URL;
   }
}
