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

    public CacheInfoScreen() {
        super(Text.literal("Cache-Informationen"));
    }

    @Override
    protected void init() {
        // Schließen-Button hinzufügen
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Schließen"), button -> this.close())
                .dimensions(this.width / 2 - 50, this.height / 2 + 20, 100, 20)
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
                this.width / 2, this.height / 2 - 10, 0xFFFFFF);
    }

    @Override
    public boolean shouldPause() {
        return false; // Spiel nicht pausieren, während das Menü geöffnet ist
    }
}