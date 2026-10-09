package Tradecorev2.client.util;

import Tradecorev2.client.config.ConfigManager;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class ServerNewsFilter {
   private static final Pattern NEWS_DELIMITER = Pattern.compile("^-+\\s*\\[\\s*news\\s*]\\s*-+$", Pattern.CASE_INSENSITIVE);
   private static final long BLOCK_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5L);
   private static boolean insideNewsBlock;
   private static long blockDeadlineNanos;

   private ServerNewsFilter() {
   }

   public static boolean shouldHide(Component message, boolean overlay) {
      if (!ConfigManager.getConfig().hideServerNews) {
         reset();
         return false;
      }

      if (overlay) {
         return false;
      }

      long now = System.nanoTime();
      if (insideNewsBlock && now > blockDeadlineNanos) {
         reset();
      }

      String text = message == null ? "" : message.getString().strip().toLowerCase(Locale.ROOT);
      if (NEWS_DELIMITER.matcher(text).matches()) {
         if (insideNewsBlock) {
            reset();
         } else {
            insideNewsBlock = true;
            blockDeadlineNanos = now + BLOCK_TIMEOUT_NANOS;
         }

         return true;
      }

      return insideNewsBlock;
   }

   public static void reset() {
      insideNewsBlock = false;
      blockDeadlineNanos = 0L;
   }
}
