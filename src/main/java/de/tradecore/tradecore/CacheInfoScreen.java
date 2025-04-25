package de.tradecore.tradecore;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting; // Formatting importieren

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.concurrent.TimeUnit; // Für Cooldown-Berechnung

public class CacheInfoScreen extends Screen {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault());
    private Text feedbackText = null;
    private long feedbackTimeout = 0;
    private ButtonWidget toggleButton;
    private ButtonWidget updateButton;
    private final int buttonSpacing = 24;

    // Cooldown-Dauer in Millisekunden (1 Stunde)
    private static final long UPDATE_COOLDOWN_MILLIS = TimeUnit.HOURS.toMillis(1);


    public CacheInfoScreen() {
        super(Text.literal("Preisdaten-Info & Einstellungen"));
    }

    private Text getToggleButtonText() {
        return Text.literal("Anzeige: " + (TradeCoreConfig.showPricesOnlyOnShift ? "Nur bei Shift" : "Immer"));
    }

    @Override
    protected void init() {
        int buttonWidth = 180;
        int buttonX = this.width / 2 - buttonWidth / 2;
        int topButtonY = this.height / 2 - 36;
        int currentY = topButtonY;

        // Button zum manuellen Aktualisieren der Preise mit Cooldown-Logik
        updateButton = ButtonWidget.builder(Text.literal("Preise jetzt aktualisieren"), button -> {
                    long lastTrigger = TradeCoreConfig.lastManualFetchTimestamp;
                    long currentTime = System.currentTimeMillis();
                    long timeSinceLast = currentTime - lastTrigger;

                    if (timeSinceLast > UPDATE_COOLDOWN_MILLIS || lastTrigger == 0) {
                        TradeCore.apiClient.triggerPriceUpdate();
                        setFeedback(Text.literal("Preis-Aktualisierung gestartet...").formatted(Formatting.GREEN), 4000);
                    } else {
                        long remainingMillis = UPDATE_COOLDOWN_MILLIS - timeSinceLast;
                        long remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis);
                        if (TimeUnit.MILLISECONDS.toSeconds(remainingMillis) % 60 > 0) remainingMinutes++;
                        remainingMinutes = Math.max(1, remainingMinutes);
                        setFeedback(Text.literal("Bitte warte noch ca. " + remainingMinutes + " Min.").formatted(Formatting.YELLOW), 3000);
                    }
                }).dimensions(buttonX, currentY, buttonWidth, 20)
                .build();
        this.addDrawableChild(updateButton);

        // Toggle-Button für Anzeige-Modus
        currentY += buttonSpacing;
        toggleButton = this.addDrawableChild(ButtonWidget.builder(getToggleButtonText(), button -> {
                    TradeCoreConfig.showPricesOnlyOnShift = !TradeCoreConfig.showPricesOnlyOnShift;
                    button.setMessage(getToggleButtonText());
                    TradeCoreConfig.saveConfig();
                }).dimensions(buttonX, currentY, buttonWidth, 20)
                .build());

        // Schließen-Button
        currentY += buttonSpacing;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Schließen"), button -> this.close())
                .dimensions(buttonX, currentY, buttonWidth, 20)
                .build());
    }

    // Hilfsmethode für Feedback
    private void setFeedback(Text message, long durationMillis) {
        this.feedbackText = message;
        this.feedbackTimeout = System.currentTimeMillis() + durationMillis;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta); // Hintergrund

        // Zeitstempel der lokalen Preisdaten anzeigen
        long lastUpdate = TradeCore.apiClient.getLastUpdateTimestamp();
        String lastUpdateText;
        int infoY = this.height / 2 - 60;

        if (lastUpdate == 0) {
            lastUpdateText = "Keine lokalen Preisdaten vorhanden.";
        } else {
            try { lastUpdateText = "Preisdaten von: " + FORMATTER.format(Instant.ofEpochSecond(lastUpdate)); }
            catch (Exception e) { lastUpdateText = "Preisdaten vorhanden (ungültiger Zeitstempel)"; }
        }
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(lastUpdateText), this.width / 2, infoY, 0xFFFFFF);

        // Cooldown-Status im Tooltip des Buttons anzeigen
        long lastTrigger = TradeCoreConfig.lastManualFetchTimestamp;
        long currentTime = System.currentTimeMillis();
        long timeSinceLast = currentTime - lastTrigger;
        boolean onCooldown = timeSinceLast <= UPDATE_COOLDOWN_MILLIS && lastTrigger != 0;

        // Rendere alle Widgets (Buttons etc.) *vor* dem Tooltip, damit der Tooltip darüber liegt
        super.render(context, mouseX, mouseY, delta);

        // Tooltip nur anzeigen, wenn Cooldown aktiv ist und der Button gehovert wird
        // Zeichne ihn NACH super.render(), damit er über anderen Elementen liegt
        if (onCooldown && updateButton.isHovered()) {
            long remainingMillis = UPDATE_COOLDOWN_MILLIS - timeSinceLast;
            long remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis);
            if (TimeUnit.MILLISECONDS.toSeconds(remainingMillis) % 60 > 0) remainingMinutes++;
            remainingMinutes = Math.max(1, remainingMinutes);
            // NEU: Zeichne Tooltip mit leichtem Offset zur Mausposition
            context.drawTooltip(this.textRenderer,
                    Text.literal("Cooldown: ca. " + remainingMinutes + " Min. übrig"),
                    mouseX + 8,  // Leicht nach rechts verschoben
                    mouseY + 8); // Leicht nach unten verschoben
        }


        // Feedback-Nachricht anzeigen (wird auch nach super.render gezeichnet)
        if (feedbackText != null && System.currentTimeMillis() < feedbackTimeout) {
            int lastButtonY = this.height / 2 - 36 + (2 * buttonSpacing); // Y des Schließen-Buttons
            int messageY = lastButtonY + 24; // Darunter
            context.drawCenteredTextWithShadow(this.textRenderer, feedbackText, this.width / 2, messageY, 0xFFFFFF); // Farbe kommt vom Text
        } else {
            feedbackText = null;
        }
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (this.client != null && this.client.player != null) this.client.setScreen(null);
        else super.close();
    }
}