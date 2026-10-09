package Tradecorev2.client.util;

import java.net.http.HttpClient;
import java.time.Duration;

public final class BdtHttpClient {
   public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10L);
   private static final HttpClient CLIENT = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5L))
      .build();

   private BdtHttpClient() {
   }

   public static HttpClient get() {
      return CLIENT;
   }

   public static boolean isSuccessful(int statusCode) {
      return statusCode >= 200 && statusCode < 300;
   }
}
