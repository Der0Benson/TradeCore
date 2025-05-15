package de.tradecore.tradecore;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries; // Für Item-ID
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.concurrent.CompletableFuture;


public class PriceSubmissionScreen extends Screen {

    private final ItemStack itemToSubmit;
    private TextFieldWidget stueckPriceField; // NEU: Feld für Stückpreis
    private TextFieldWidget stackPriceField;
    private TextFieldWidget dkPriceField;
    private ButtonWidget initialSubmitButton;
    private ButtonWidget confirmButton;
    private ButtonWidget cancelButton;
    private ButtonWidget closeButton;

    private Text feedbackText = null;
    private long feedbackTimeout = 0;
    private boolean confirming = false;

    // Zwischenspeicher
    private int confirmedStueckPrice; // NEU
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
        int inputWidth = 150; // Etwas breiter für mehr Platz oder kleinere Schrift
        int inputX = centerX - inputWidth / 2;
        int topY = this.height / 2 - 50; // Etwas höher starten für 3 Felder
        int spacingY = 25; // Abstand zwischen Feldern
        int buttonY = topY + 3 * spacingY + 5; // Button-Position unter den drei Feldern

        // NEU: Textfeld für Stückpreis
        stueckPriceField = new TextFieldWidget(this.textRenderer, inputX, topY, inputWidth, 20, Text.literal("Stückpreis"));
        stueckPriceField.setPlaceholder(Text.literal("Stückpreis...").formatted(Formatting.GRAY));
        stueckPriceField.setTextPredicate(s -> s.matches("[0-9]*")); // Nur Zahlen erlauben
        this.addDrawableChild(stueckPriceField);

        // Textfeld für Stackpreis
        stackPriceField = new TextFieldWidget(this.textRenderer, inputX, topY + spacingY, inputWidth, 20, Text.literal("Stackpreis"));
        stackPriceField.setPlaceholder(Text.literal("Stackpreis...").formatted(Formatting.GRAY));
        stackPriceField.setTextPredicate(s -> s.matches("[0-9]*"));
        this.addDrawableChild(stackPriceField);

        // Textfeld für DK-Preis
        dkPriceField = new TextFieldWidget(this.textRenderer, inputX, topY + 2 * spacingY, inputWidth, 20, Text.literal("DK-Preis"));
        dkPriceField.setPlaceholder(Text.literal("DK-Preis...").formatted(Formatting.GRAY));
        dkPriceField.setTextPredicate(s -> s.matches("[0-9]*"));
        this.addDrawableChild(dkPriceField);

        // Initialer Button
        initialSubmitButton = ButtonWidget.builder(Text.literal("Preis prüfen & senden"), button -> startConfirmation())
                .dimensions(centerX - 75, buttonY, 150, 20).build();
        this.addDrawableChild(initialSubmitButton);

        // Bestätigungsbuttons (initial unsichtbar)
        confirmButton = ButtonWidget.builder(Text.literal("Bestätigen"), button -> executeSubmit())
                .dimensions(centerX - 75, buttonY, 70, 20).build();
        confirmButton.visible = false; this.addDrawableChild(confirmButton);
        cancelButton = ButtonWidget.builder(Text.literal("Abbrechen"), button -> cancelConfirmation())
                .dimensions(centerX + 5, buttonY, 70, 20).build();
        cancelButton.visible = false; this.addDrawableChild(cancelButton);

        // Schließen Button
        closeButton = ButtonWidget.builder(Text.literal("Schließen"), button -> this.close())
                .dimensions(centerX - 75, buttonY + 24, 150, 20).build();
        this.addDrawableChild(closeButton);

        this.setInitialFocus(stueckPriceField); // Fokus auf das erste Feld (Stückpreis)
        updateConfirmingState();
    }

    // Startet Bestätigung
    private void startConfirmation() {
        if (this.client == null || this.client.player == null) return;
        String stueckPriceStr = stueckPriceField.getText(); // NEU
        String stackPriceStr = stackPriceField.getText();
        String dkPriceStr = dkPriceField.getText();

        // Mindestens ein Preis muss eingegeben werden
        if (stueckPriceStr.isEmpty() && stackPriceStr.isEmpty() && dkPriceStr.isEmpty()) {
            setFeedback(Text.literal("Bitte mindestens einen Preis eingeben.").formatted(Formatting.RED), 3000);
            return;
        }

        try {
            // Parse Preise, setze auf 0 wenn leer, damit die API es als "nicht angegeben" oder 0 interpretieren kann
            int stueckPrice = stueckPriceStr.isEmpty() ? 0 : Integer.parseInt(stueckPriceStr); // NEU
            int stackPrice = stackPriceStr.isEmpty() ? 0 : Integer.parseInt(stackPriceStr);
            int dkPrice = dkPriceStr.isEmpty() ? 0 : Integer.parseInt(dkPriceStr);

            if (stueckPrice < 0 || stackPrice < 0 || dkPrice < 0) { // NEU: stueckPrice Prüfung
                setFeedback(Text.literal("Preise dürfen nicht negativ sein.").formatted(Formatting.RED), 3000);
                return;
            }

            this.confirmedStueckPrice = stueckPrice; // NEU
            this.confirmedStackPrice = stackPrice;
            this.confirmedDkPrice = dkPrice;
            this.confirmedItemName = Registries.ITEM.getId(itemToSubmit.getItem()).toString();
            this.confirming = true;
            updateConfirmingState();

            // Feedbacktext anpassen
            StringBuilder feedbackBuilder = new StringBuilder("Sende ");
            boolean hasPrice = false;
            if (stueckPrice > 0) {
                feedbackBuilder.append("Stück: ").append(stueckPrice).append("$");
                hasPrice = true;
            }
            if (stackPrice > 0) {
                if (hasPrice) feedbackBuilder.append(", ");
                feedbackBuilder.append("Stack: ").append(stackPrice).append("$");
                hasPrice = true;
            }
            if (dkPrice > 0) {
                if (hasPrice) feedbackBuilder.append(", ");
                feedbackBuilder.append("DK: ").append(dkPrice).append("$");
            }
            feedbackBuilder.append("?");

            setFeedback(Text.literal(feedbackBuilder.toString()).formatted(Formatting.YELLOW), 10000);

        } catch (NumberFormatException e) {
            setFeedback(Text.literal("Ungültige Zahl eingegeben.").formatted(Formatting.RED), 3000);
        }
    }

    // Führt API Call aus
    private void executeSubmit() {
        if (this.client == null || this.client.player == null) return;
        confirmButton.active = false; cancelButton.active = false; confirmButton.setMessage(Text.literal("Sende..."));
        setFeedback(Text.literal("Sende Preisvorschlag...").formatted(Formatting.YELLOW), 5000);
        String playerName = client.player.getName().getString(); String playerUuid = client.player.getUuidAsString();

        // NEU: confirmedStueckPrice wird übergeben
        CompletableFuture<Boolean> future = TradeCore.apiClient.submitPriceSuggestion(
                confirmedItemName,
                confirmedStueckPrice,
                confirmedStackPrice,
                confirmedDkPrice,
                playerName,
                playerUuid
        );

        future.thenAcceptAsync(success -> {
            if (success) {
                setFeedback(Text.literal("Preis erfolgreich gesendet!").formatted(Formatting.GREEN), 3000);
                stueckPriceField.setText(""); // NEU
                stackPriceField.setText("");
                dkPriceField.setText("");
                this.confirming = false;
                updateConfirmingState();
            } else {
                setFeedback(Text.literal("Fehler beim Senden. Evtl. Zeitlimit? (Logs!)").formatted(Formatting.RED), 4000);
                this.confirming = false; // Damit der Benutzer es erneut versuchen kann, ohne den Screen neu öffnen zu müssen
                updateConfirmingState(); // Buttons wieder korrekt setzen
            }
            // Stelle sicher, dass die Buttons im Hauptthread aktualisiert werden
            client.execute(() -> {
                confirmButton.setMessage(Text.literal("Bestätigen"));
                // Die Aktivität wird durch updateConfirmingState() gesteuert
            });
        }, MinecraftClient.getInstance());
    }

    // Bricht Bestätigung ab
    private void cancelConfirmation() {
        this.confirming = false; updateConfirmingState(); setFeedback(null, 0);
    }

    // Aktualisiert Sichtbarkeit der Widgets
    private void updateConfirmingState() {
        boolean fieldsVisible = !confirming;
        stueckPriceField.setVisible(fieldsVisible); // NEU
        stackPriceField.setVisible(fieldsVisible);
        dkPriceField.setVisible(fieldsVisible);
        initialSubmitButton.visible = fieldsVisible;
        initialSubmitButton.active = fieldsVisible; // Stelle sicher, dass der Button aktiv ist, wenn sichtbar

        confirmButton.visible = confirming;
        confirmButton.active = confirming; // Stelle sicher, dass der Button aktiv ist, wenn sichtbar
        cancelButton.visible = confirming;
        cancelButton.active = confirming; // Stelle sicher, dass der Button aktiv ist, wenn sichtbar

        this.setInitialFocus(confirming ? confirmButton : stueckPriceField);
    }

    // Setzt Feedback-Text
    private void setFeedback(Text message, long durationMillis) {
        this.feedbackText = message; this.feedbackTimeout = System.currentTimeMillis() + durationMillis;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        int itemX = this.width / 2 - 8;
        int itemY = this.height / 2 - 100; // Etwas höher für mehr Platz für Felder
        context.drawItem(itemToSubmit, itemX, itemY);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        // Labels nur im Eingabemodus
        if (!confirming) {
            // Labels links neben den Textfeldern positionieren
            int labelXOffset = 5; // Abstand des Labels vom Textfeld
            int stueckLabelX = stueckPriceField.getX() - this.textRenderer.getWidth("Stückpreis:") - labelXOffset;
            context.drawTextWithShadow(this.textRenderer, Text.literal("Stückpreis:"), stueckLabelX, stueckPriceField.getY() + 6, 0xFFFFFF);

            int stackLabelX = stackPriceField.getX() - this.textRenderer.getWidth("Stackpreis:") - labelXOffset;
            context.drawTextWithShadow(this.textRenderer, Text.literal("Stackpreis:"), stackLabelX, stackPriceField.getY() + 6, 0xFFFFFF);

            int dkLabelX = dkPriceField.getX() - this.textRenderer.getWidth("DK-Preis:") - labelXOffset;
            context.drawTextWithShadow(this.textRenderer, Text.literal("DK-Preis:"), dkLabelX, dkPriceField.getY() + 6, 0xFFFFFF);
        }

        super.render(context, mouseX, mouseY, delta);

        Text bottomText = null;
        if (feedbackText != null && System.currentTimeMillis() < feedbackTimeout) {
            bottomText = feedbackText;
        } else {
            feedbackText = null;
            bottomText = Text.literal("Hinweis: Absenden nutzt UUID zur Spam-Verhinderung.")
                    .formatted(Formatting.DARK_GRAY, Formatting.ITALIC);
        }
        if (bottomText != null) {
            int feedbackY = this.height - this.textRenderer.fontHeight - 5;
            context.drawCenteredTextWithShadow(this.textRenderer, bottomText, this.width / 2, feedbackY, 0xFFFFFF);
        }
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() { if (this.client != null) this.client.setScreen(null); else super.close(); }
}