package de.tradecore.legacy.util;

import de.tradecore.legacy.config.ConfigManager;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ServerNewsFilter {
    private static final Pattern DELIMITER = Pattern.compile("^-+\\s*\\[\\s*news\\s*]\\s*-+$", Pattern.CASE_INSENSITIVE);
    private static boolean inside;
    private static long deadline;
    private ServerNewsFilter() {}

    public static boolean shouldHide(String message) {
        if (!ConfigManager.get().hideServerNews) { reset(); return false; }
        long now = System.nanoTime();
        if (inside && now > deadline) reset();
        String text = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
        if (DELIMITER.matcher(text).matches()) {
            inside = !inside;
            deadline = now + 5_000_000_000L;
            return true;
        }
        return inside;
    }

    public static void reset() { inside = false; deadline = 0L; }
}
