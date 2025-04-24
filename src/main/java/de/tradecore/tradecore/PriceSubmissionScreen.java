package de.tradecore.tradecore;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.concurrent.CompletableFuture;

public class PriceSubmissionScreen extends Screen {

    private final ItemStack itemToSubmit;
    private TextFieldWidget stackPriceField;
    private TextFieldWidget dkPriceField;
    private ButtonWidget initialSubmitButton; // Umbenannt
    private ButtonWidget confirmButton;       // NEU
    private ButtonWidget cancelButton;        // NEU
    private ButtonWidget closeButton;         // NEU (separater Schließen-Button)

    private Text feedbackText = null;
    private long feedbackTimeout = 0;
    private boolean confirming = false; // NEU: Zustand für Bestätigung

    // Zwischenspeicher für Bestätigung
    private int confirmedStackPrice;
    private int confirmedDkPrice;
    private String confirmedItemName;


    public PriceSubmissionScreen(ItemStack item) {
        super(Text.literal("Preis vorschlagen für: ").append(item.getName()));
        this.itemToSubmit = item.copy();
    }

    @Override
    protected void init() {
        if (this.client == null) return;

        int centerX = this.width / 2;
        int inputWidth = 100;
        int inputX = centerX - inputWidth / 2;
        int topY = this.height / 2 - 30;
        int spacingY = 30;
        int buttonY = topY + 2 * spacingY; // Start Y für Buttons unter den Feldern

        // Stackpreis Feld
        stackPriceField = new TextFieldWidget(this.textRenderer, inputX, topY, inputWidth, 20, Text.literal("Stackpreis"));
        stackPriceField.setPlaceholder(Text.literal("Stackpreis...").formatted(Formatting.GRAY));
        stackPriceField.setTextPredicate(s -> s.matches("[0-9]*"));
        this.addDrawableChild(stackPriceField);

        // DK-Preis Feld
        dkPriceField = new TextFieldWidget(this.textRenderer, inputX, topY + spacingY, inputWidth, 20, Text.literal("DK-Preis"));
        dkPriceField.setPlaceholder(Text.literal("DK-Preis...").formatted(Formatting.GRAY));
        dkPriceField.setTextPredicate(s -> s.matches("[0-9]*"));
        this.addDrawableChild(dkPriceField);

        // --- Initialer Zustand ---
        // Ursprünglicher Senden-Button
        initialSubmitButton = ButtonWidget.builder(Text.literal("Preis prüfen & senden"), button -> startConfirmation())
                .dimensions(centerX - 75, buttonY, 150, 20)
                .build();
        this.addDrawableChild(initialSubmitButton);

        // --- Bestätigungszustand (initial unsichtbar) ---
        // Bestätigen-Button
        confirmButton = ButtonWidget.builder(Text.literal("Bestätigen"), button -> executeSubmit())
                .dimensions(centerX - 75, buttonY, 70, 20) // Schmaler Button
                .build();
        confirmButton.visible = false; // Initial unsichtbar
        this.addDrawableChild(confirmButton);

        // Abbrechen-Button (neben Bestätigen)
        cancelButton = ButtonWidget.builder(Text.literal("Abbrechen"), button -> cancelConfirmation())
                .dimensions(centerX + 5, buttonY, 70, 20) // Schmaler Button, rechts daneben
                .build();
        cancelButton.visible = false; // Initial unsichtbar
        this.addDrawableChild(cancelButton);

        // Schließen-Button (immer sichtbar, unter den anderen Buttons)
        closeButton = ButtonWidget.builder(Text.literal("Schließen"), button -> this.close())
                .dimensions(centerX - 75, buttonY + 24, 150, 20)
                .build();
        this.addDrawableChild(closeButton);


        this.setInitialFocus(stackPriceField);
        updateConfirmingState(); // Setzt die Sichtbarkeit basierend auf 'confirming'
    }

    // Startet den Bestätigungsprozess
    private void startConfirmation() {
        if (this.client == null || this.client.player == null) return;

        String stackPriceStr = stackPriceField.getText();
        String dkPriceStr = dkPriceField.getText();

        if (stackPriceStr.isEmpty() || dkPriceStr.isEmpty()) {
            setFeedback(Text.literal("Bitte beide Preise eingeben.").formatted(Formatting.RED), 3000);
            return;
        }

        try {
            int stackPrice = Integer.parseInt(stackPriceStr);
            int dkPrice = Integer.parseInt(dkPriceStr);

            if (stackPrice < 0 || dkPrice < 0) {
                setFeedback(Text.literal("Preise dürfen nicht negativ sein.").formatted(Formatting.RED), 3000);
                return;
            }

            // Daten für Bestätigung speichern
            this.confirmedStackPrice = stackPrice;
            this.confirmedDkPrice = dkPrice;
            this.confirmedItemName = Registries.ITEM.getId(itemToSubmit.getItem()).toString();

            // In den Bestätigungsmodus wechseln
            this.confirming = true;
            updateConfirmingState();
            setFeedback(Text.literal("Sende Stack: " + stackPrice + "$, DK: " + dkPrice + "$?").formatted(Formatting.YELLOW), 10000); // Längere Anzeige

        } catch (NumberFormatException e) {
            setFeedback(Text.literal("Ungültige Zahl eingegeben.").formatted(Formatting.RED), 3000);
        }
    }

    // Führt den eigentlichen API-Call aus
    private void executeSubmit() {
        if (this.client == null || this.client.player == null) return;

        // Deaktiviere Buttons während des Sendens
        confirmButton.active = false;
        cancelButton.active = false; // Auch Abbrechen deaktivieren
        confirmButton.setMessage(Text.literal("Sende..."));
        setFeedback(Text.literal("Sende Preisvorschlag...").formatted(Formatting.YELLOW), 5000);

        String playerName = client.player.getName().getString();
        String playerUuid = client.player.getUuidAsString();

        CompletableFuture<Boolean> future = TradeCore.apiClient.submitPriceSuggestion(
                confirmedItemName, confirmedStackPrice, confirmedDkPrice, playerName, playerUuid
        );

        future.thenAcceptAsync(success -> {
            if (success) {
                setFeedback(Text.literal("Preis erfolgreich gesendet!").formatted(Formatting.GREEN), 3000);
                // Felder leeren und zurück zum Anfangszustand
                stackPriceField.setText("");
                dkPriceField.setText("");
                this.confirming = false; // Zurück zum Eingabemodus
                updateConfirmingState();
            } else {
                // Hier könnte die spezifischere Fehlermeldung (z.B. wegen Zeitlimit) angezeigt werden
                // Annahme: PriceAPIClient gibt bei bekanntem Fehler eine spezifische Exception oder Nachricht zurück
                // Fürs Erste eine generische Meldung:
                setFeedback(Text.literal("Fehler beim Senden. Evtl. Zeitlimit? (Siehe Logs)").formatted(Formatting.RED), 4000);
                // Buttons wieder aktivieren, damit der Nutzer es ggf. korrigieren oder abbrechen kann
                this.confirming = false; // Zurück zur Eingabe
                updateConfirmingState(); // Reaktiviert initialSubmitButton etc.
            }
            // Stelle sicher, dass die Bestätigungsbuttons zurückgesetzt werden (falls nicht schon durch updateConfirmingState)
            confirmButton.setMessage(Text.literal("Bestätigen"));

        }, MinecraftClient.getInstance());
    }

    // Bricht den Bestätigungsvorgang ab
    private void cancelConfirmation() {
        this.confirming = false;
        updateConfirmingState();
        setFeedback(null, 0); // Feedback löschen
    }

    // Aktualisiert die Sichtbarkeit der Elemente basierend auf dem 'confirming'-Status
    private void updateConfirmingState() {
        stackPriceField.setVisible(!confirming);
        dkPriceField.setVisible(!confirming);
        initialSubmitButton.visible = !confirming;
        initialSubmitButton.active = !confirming; // Aktivieren/Deaktivieren

        confirmButton.visible = confirming;
        confirmButton.active = confirming; // Aktivieren/Deaktivieren
        cancelButton.visible = confirming;
        cancelButton.active = confirming; // Aktivieren/Deaktivieren

        // Fokus entsprechend setzen
        if (confirming) {
            this.setInitialFocus(confirmButton);
        } else {
            this.setInitialFocus(stackPriceField);
        }
    }


    private void setFeedback(Text message, long durationMillis) {
        this.feedbackText = message;
        this.feedbackTimeout = System.currentTimeMillis() + durationMillis;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int itemX = this.width / 2 - 8;
        int itemY = this.height / 2 - 80;
        context.drawItem(itemToSubmit, itemX, itemY);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        // Labels nur anzeigen, wenn nicht im Bestätigungsmodus
        if (!confirming) {
            int labelX = stackPriceField.getX() - 5 - this.textRenderer.getWidth("Stackpreis:");
            context.drawTextWithShadow(this.textRenderer, Text.literal("Stackpreis:"), labelX, stackPriceField.getY() + 6, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("DK-Preis:"), labelX, dkPriceField.getY() + 6, 0xFFFFFF);
        }

        super.render(context, mouseX, mouseY, delta); // Rendert alle sichtbaren Widgets

        // Feedback-Nachricht unten rendern
        if (feedbackText != null && System.currentTimeMillis() < feedbackTimeout) {
            int feedbackY = this.height / 2 + 80; // Position unter den Buttons
            context.drawCenteredTextWithShadow(this.textRenderer, feedbackText, this.width / 2, feedbackY, 0xFFFFFF);
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