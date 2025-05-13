package de.tradecore.tradecore;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter; // NEU: Importieren
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class TradeCoreHudOverlay implements HudRenderCallback {

    @Override
    // Die Signatur der Methode wurde an die aktuelle Fabric API angepasst
    public void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return;
        }

        // Der tickDelta-Wert wird nun vom tickCounter-Objekt geholt
        // float tickDelta = tickCounter.tickDelta; // tickDelta ist ein public field in RenderTickCounter

        // Banner nur anzeigen, wenn der Status kürzlich geändert wurde
        if (TradeCoreConfig.lastModStatusChangeTime > 0 &&
                System.currentTimeMillis() - TradeCoreConfig.lastModStatusChangeTime < TradeCoreConfig.BANNER_DISPLAY_DURATION_MS) {

            Text statusText = Text.literal("TradeCore: ")
                    .append(TradeCoreConfig.modEnabled ?
                            Text.literal("An").formatted(Formatting.GREEN) :
                            Text.literal("Aus").formatted(Formatting.RED));

            int screenWidth = client.getWindow().getScaledWidth();
            int textWidth = client.textRenderer.getWidth(statusText);
            int x = screenWidth - textWidth - 10; // Oben rechts mit 10px Abstand
            int y = 10;                             // 10px von oben

            // Optionaler Hintergrund für bessere Lesbarkeit:
            // int backgroundColor = 0x80000000; // Schwarz mit 50% Transparenz
            // drawContext.fill(RenderLayer.getGuiOverlay(), x - 3, y - 3, x + textWidth + 3, y + client.textRenderer.fontHeight + 2, backgroundColor);
            // Beachte: drawContext.fill benötigt ggf. einen RenderLayer in neueren Versionen oder die Parameter sind anders.
            // Eine einfachere Variante für einen gefüllten Hintergrund ist:
            // drawContext.fill(x - 2, y - 2, x + textWidth + 2, y + client.textRenderer.fontHeight + 2, 0x80000000);


            drawContext.drawTextWithShadow(client.textRenderer, statusText, x, y, 0xFFFFFF); // Weißer Text
        } else if (System.currentTimeMillis() - TradeCoreConfig.lastModStatusChangeTime >= TradeCoreConfig.BANNER_DISPLAY_DURATION_MS) {
            // Setze den Zeitstempel zurück, damit das Banner nicht bei jedem Tick neu geprüft wird, wenn es nicht mehr angezeigt werden soll.
            TradeCoreConfig.lastModStatusChangeTime = 0L;
        }
    }
}