package de.tradecore.tradecore;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class CacheInfoScreen extends Screen {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private String updateMessage = null;
    private long updateMessageTimeout = 0;

    public CacheInfoScreen() {
        super(Text.literal("Cache-Informationen"));
    }

    @Override
    protected void init() {
        // Schließen-Button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Schließen"), button -> this.close())
                .dimensions(this.width / 2 - 50, this.height / 2 + 20, 100, 20)
                .build());

        // Update-Button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cache Aktualisieren"), button -> {
                    TradeCore.apiClient.clearCache();
                    this.updateMessage = "Cache wurde aktualisiert!";
                    this.updateMessageTimeout = System.currentTimeMillis() + 3000; // Nachricht für 3 Sekunden anzeigen
                }).dimensions(this.width / 2 - 50, this.height / 2 - 10, 100, 20)
                .build());
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        // Hintergrund rendern
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        // Letzte Aktualisierungszeit anzeigen
        long lastUpdate = TradeCore.apiClient.getLastUpdateTimestamp();
        String lastUpdateText = lastUpdate == 0
                ? "Keine Aktualisierung bisher"
                : "Letzte Aktualisierung: " + FORMATTER.format(Instant.ofEpochSecond(lastUpdate));

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(lastUpdateText),
                this.width / 2, this.height / 2 - 30, 0xFFFFFF);

        // Bestätigungsnachricht anzeigen, falls vorhanden
        if (updateMessage != null && System.currentTimeMillis() < updateMessageTimeout) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(updateMessage),
                    this.width / 2, this.height / 2 + 40, 0x00FF00);
        } else {
            updateMessage = null; // Nachricht entfernen, wenn Timeout abgelaufen
        }
    }

    @Override
    public boolean shouldPause() {
        return false; // Spiel nicht pausieren, während das Menü geöffnet ist
    }
}