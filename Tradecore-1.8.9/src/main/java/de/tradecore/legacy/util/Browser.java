package de.tradecore.legacy.util;

import java.awt.Desktop;
import java.net.URI;

public final class Browser {
    public static final String DISCORD_URL = "https://discord.gg/tradecore";
    private Browser() {}

    public static void open(String url) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(new URI(url));
        } catch (Exception exception) {
            Notifications.error("Der Link konnte nicht geöffnet werden.");
        }
    }
}
