package Tradecorev2.client.util;

import java.util.Locale;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class HighlightColorUtil {
   private static final Pattern HEX_PATTERN = Pattern.compile("^#?[0-9a-fA-F]{6}$");
   public static final String DEFAULT_COLOR = "#FFAA00";

   private HighlightColorUtil() {
   }

   public static boolean isValidHex(String value) {
      return value == null ? false : HEX_PATTERN.matcher(value.trim()).matches();
   }

   public static String normalizeHex(String value) {
      if (!isValidHex(value)) {
         return "#FFAA00";
      } else {
         String trimmed = value.trim();
         if (trimmed.charAt(0) != '#') {
            trimmed = "#" + trimmed;
         }

         return trimmed.toUpperCase(Locale.ROOT);
      }
   }

   public static float[] toNormalizedRgb(String value) {
      String normalized = normalizeHex(value);
      int rgb = Integer.parseInt(normalized.substring(1), 16);
      float r = (float)(rgb >> 16 & 255) / 255.0F;
      float g = (float)(rgb >> 8 & 255) / 255.0F;
      float b = (float)(rgb & 255) / 255.0F;
      return new float[]{Math.max(r, 0.0F), Math.max(g, 0.0F), Math.max(b, 0.0F)};
   }
}
