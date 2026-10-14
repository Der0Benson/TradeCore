package de.tradecore.legacy.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class ApiHttp {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private int index;
        @Override public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Tradecore-HTTP-" + (++index));
            thread.setDaemon(true);
            return thread;
        }
    });

    private ApiHttp() {}

    public static void get(final String url, final Callback callback) {
        request("GET", url, "", callback);
    }

    public static void post(final String url, final String body, final Callback callback) {
        request("POST", url, body == null ? "" : body, callback);
    }

    private static void request(final String method, final String url, final String body, final Callback callback) {
        EXECUTOR.execute(new Runnable() {
            @Override public void run() {
                HttpURLConnection connection = null;
                try {
                    long timestamp = System.currentTimeMillis() / 1000L;
                    connection = (HttpURLConnection) new URL(url).openConnection();
                    connection.setRequestMethod(method);
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(10000);
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setRequestProperty("X-Timestamp", String.valueOf(timestamp));
                    connection.setRequestProperty("X-Signature", HmacSigner.sign(body, timestamp));
                    if ("POST".equals(method)) {
                        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                        connection.setDoOutput(true);
                        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                        connection.setFixedLengthStreamingMode(payload.length);
                        try (OutputStream output = connection.getOutputStream()) { output.write(payload); }
                    }
                    int status = connection.getResponseCode();
                    InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                    callback.complete(new Response(status, read(stream)), null);
                } catch (Exception exception) {
                    callback.complete(null, exception);
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        });
    }

    private static String read(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line).append('\n');
        }
        return result.toString().trim();
    }

    public interface Callback {
        void complete(Response response, Exception error);
    }

    public static final class Response {
        public final int status;
        public final String body;
        public Response(int status, String body) { this.status = status; this.body = body; }
        public boolean successful() { return status >= 200 && status < 300; }
    }
}
