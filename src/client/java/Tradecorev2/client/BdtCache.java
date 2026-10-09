package Tradecorev2.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class BdtCache {
   private static final Logger LOGGER = LoggerFactory.getLogger("TradecoreV2-BdtCache");
   private static BdtData currentBdt;
   private static long lastUpdatedAt;

   public static void setBdtData(BdtData data) {
      currentBdt = data;
      lastUpdatedAt = data == null ? 0L : System.currentTimeMillis();
   }

   public static BdtData getBdtData() {
      return currentBdt;
   }

   public static boolean hasBdtData() {
      boolean has = currentBdt != null;
      if (has) {
         LOGGER.info("BDT-Daten im Cache vorhanden: {}", currentBdt.item_name);
      }

      return has;
   }

   public static long getLastUpdatedAt() {
      return lastUpdatedAt;
   }

   public static void initializeOnGameStart() {
      currentBdt = null;
      lastUpdatedAt = 0L;
   }

   public static void clearLocalData() {
      currentBdt = null;
      lastUpdatedAt = 0L;
   }

   public static void forceRefresh() {
      currentBdt = null;
      lastUpdatedAt = 0L;
   }
}
