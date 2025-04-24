package de.tradecore.tradecore;

import net.minecraft.client.gui.DrawContext; // Korrekter Import für DrawContext
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle; // Für flexibleres Datumsformat

public class CacheInfoScreen extends Screen {
    // Verwende ein Standardformat oder ein spezifischeres
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM) // z.B. 24.04.2025, 19:30:15
            .withZone(ZoneId.systemDefault());
    private String updateMessage = null;
    private long updateMessageTimeout = 0;
    private ButtonWidget toggleButton;

    // NEU: buttonSpacing als Klassenvariable definieren
    private final int buttonSpacing = 24;

    public CacheInfoScreen() {
        super(Text.literal("Preisdaten-Info & Einstellungen"));
    }

    private Text getToggleButtonText() {
        return Text.literal("Anzeige: " + (TradeCore.showPricesOnlyOnShift ? "Nur bei Shift" : "Immer"));
    }

    @Override
    protected void init() {
        int buttonWidth = 180;
        int buttonX = this.width / 2 - buttonWidth / 2;
        int topButtonY = this.height / 2 - 36;
        // int buttonSpacing = 24; // Entfernt: Jetzt eine Klassenvariable

        // Button zum manuellen Aktualisieren der Preise
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Preise jetzt aktualisieren"), button -> {
                    TradeCore.apiClient.triggerPriceUpdate();
                    this.updateMessage = "Preis-Aktualisierung von API gestartet...";
                    this.updateMessageTimeout = System.currentTimeMillis() + 4000;
                }).dimensions(buttonX, topButtonY, buttonWidth, 20)
                .build());

        // Toggle-Button für Anzeige-Modus
        int currentY = topButtonY + buttonSpacing; // Nutzt jetzt die Klassenvariable
        toggleButton = this.addDrawableChild(ButtonWidget.builder(getToggleButtonText(), button -> {
                    TradeCore.showPricesOnlyOnShift = !TradeCore.showPricesOnlyOnShift;
                    button.setMessage(getToggleButtonText());
                    TradeCoreConfig.saveConfig();
                    TradeCore.LOGGER.info("Einstellung 'showPricesOnlyOnShift' gespeichert: {}", TradeCore.showPricesOnlyOnShift);
                }).dimensions(buttonX, currentY, buttonWidth, 20)
                .build());

        // Schließen-Button
        currentY += buttonSpacing; // Nutzt jetzt die Klassenvariable
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Schließen"), button -> this.close())
                .dimensions(buttonX, currentY, buttonWidth, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Hintergrund rendern
        this.renderBackground(context, mouseX, mouseY, delta);

        // Zeitstempel der lokalen Preisdaten anzeigen
        long lastUpdate = TradeCore.apiClient.getLastUpdateTimestamp();
        String lastUpdateText;
        int infoY = this.height / 2 - 60;

        if (lastUpdate == 0) {
            lastUpdateText = "Keine lokalen Preisdaten vorhanden.";
        } else {
            try {
                lastUpdateText = "Preisdaten von: " + FORMATTER.format(Instant.ofEpochSecond(lastUpdate));
            } catch (Exception e) {
                lastUpdateText = "Preisdaten vorhanden (ungültiger Zeitstempel)";
                TradeCore.LOGGER.warn("Ungültiger Zeitstempel für Preisdaten: {}", lastUpdate);
            }
        }
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(lastUpdateText),
                this.width / 2, infoY, 0xFFFFFF);

        // Wichtig: Buttons und andere Widgets nach dem Hintergrund und Text rendern
        super.render(context, mouseX, mouseY, delta);

        // Bestätigungsnachricht anzeigen (unter den Buttons)
        if (updateMessage != null && System.currentTimeMillis() < updateMessageTimeout) {
            // KORRIGIERT: Berechnet die Position korrekt unter Verwendung der Klassenvariable buttonSpacing
            int topButtonY = this.height / 2 - 36; // Muss hier bekannt sein oder berechnet werden
            // Annahme: 3 Buttons, Start bei topButtonY, Abstand buttonSpacing
            int lastButtonY = topButtonY + (2 * buttonSpacing); // Y-Position des unteren Randes des letzten Buttons ist topButtonY + 2*spacing + 20 (Höhe)
            // Wir wollen aber die Y-Koordinate *unter* dem Button
            int messageY = lastButtonY + 24; // Platzieren wir es 24 Pixel unter dem letzten Button (Start-Y)

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