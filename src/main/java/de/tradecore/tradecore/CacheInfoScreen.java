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
    private ButtonWidget toggleButton;

    public CacheInfoScreen() {
        super(Text.literal("Cache-Informationen & Einstellungen"));
    }

    private Text getToggleButtonText() {
        return Text.literal("Anzeige: " + (TradeCore.showPricesOnlyOnShift ? "Nur bei Shift" : "Immer"));
    }

    @Override
    protected void init() {
        int buttonWidth = 150;
        int buttonX = this.width / 2 - buttonWidth / 2;
        int buttonY = this.height / 2 - 10;

        // Update-Button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cache Aktualisieren"), button -> {
                    TradeCore.apiClient.clearCache();
                    this.updateMessage = "Cache wurde aktualisiert!";
                    this.updateMessageTimeout = System.currentTimeMillis() + 3000;
                }).dimensions(buttonX, buttonY, buttonWidth, 20)
                .build());

        // Toggle-Button für Anzeige-Modus
        buttonY += 24;
        toggleButton = this.addDrawableChild(ButtonWidget.builder(getToggleButtonText(), button -> {
                    // 1. Wert umschalten
                    TradeCore.showPricesOnlyOnShift = !TradeCore.showPricesOnlyOnShift;
                    // 2. Button-Text aktualisieren
                    button.setMessage(getToggleButtonText());
                    // 3. NEU: Geänderte Einstellung speichern
                    TradeCoreConfig.saveConfig();
                    TradeCore.LOGGER.info("Einstellung 'showPricesOnlyOnShift' gespeichert: {}", TradeCore.showPricesOnlyOnShift); // Log für Bestätigung
                }).dimensions(buttonX, buttonY, buttonWidth, 20)
                .build());

        // Schließen-Button
        buttonY += 24;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Schließen"), button -> this.close())
                .dimensions(buttonX, buttonY, buttonWidth, 20)
                .build());
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        // Letzte Aktualisierungszeit
        long lastUpdate = TradeCore.apiClient.getLastUpdateTimestamp();
        String lastUpdateText = lastUpdate == 0
                ? "Keine Aktualisierung bisher"
                : "Letzte Aktualisierung: " + FORMATTER.format(Instant.ofEpochSecond(lastUpdate));
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(lastUpdateText),
                this.width / 2, this.height / 2 - 50, 0xFFFFFF);

        // Buttons rendern
        super.render(context, mouseX, mouseY, delta);

        // Bestätigungsnachricht
        if (updateMessage != null && System.currentTimeMillis() < updateMessageTimeout) {
            int messageY = this.height / 2 - 10 + 24 + 24 + 24;
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(updateMessage),
                    this.width / 2, messageY, 0x00FF00);
        } else {
            updateMessage = null;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}