package Tradecorev2.client;

import java.awt.Desktop;
import java.net.URI;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class DiscordOpener {
   private static final Logger LOGGER = LoggerFactory.getLogger("TradecoreV2-DiscordOpener");
   private static final String DISCORD_INVITE_URL = "https://discord.gg/cFxPGz8URS";

   public static void openDiscordInvite() {
      try {
         if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new UnsupportedOperationException("Das Öffnen von Weblinks wird auf diesem System nicht unterstützt.");
         }

         Desktop.getDesktop().browse(new URI(DISCORD_INVITE_URL));
      } catch (Exception e) {
         LOGGER.error("Konnte den Discord-Link nicht öffnen: {}", "https://discord.gg/cFxPGz8URS", e);
      }

   }
}
