package Tradecorev2.client.util;

import java.net.http.HttpResponse;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public final class ApiDebugLogger {
   private static final int MAX_PREVIEW_LENGTH = 600;

   private ApiDebugLogger() {
   }

   public static void log(Logger logger, String context, HttpResponse<String> response) {
      if (logger != null) {
         if (response == null) {
            logger.warn("{} | response=null", context);
         } else {
            String body = (String)response.body();
            String preview = toPreview(body);
            int status = response.statusCode();
            int length = body != null ? body.length() : 0;
            if (status >= 200 && status < 300) {
               logger.info("{} | status={} body_length={} body_preview={}", new Object[]{context, status, length, preview});
            } else {
               logger.warn("{} | status={} body_length={} body_preview={}", new Object[]{context, status, length, preview});
            }

         }
      }
   }

   private static String toPreview(String body) {
      if (body == null) {
         return "<null>";
      } else {
         String singleLine = body.replace('\n', ' ').replace('\r', ' ').trim();
         return singleLine.length() > 600 ? singleLine.substring(0, 600) + "...(truncated)" : singleLine;
      }
   }
}
