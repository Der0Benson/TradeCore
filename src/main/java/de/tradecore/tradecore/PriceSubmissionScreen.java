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
        int centerX = this.width / 2; int inputWidth = 100; int inputX = centerX - inputWidth / 2;
        int topY = this.height / 2 - 30; int spacingY = 30; int buttonY = topY + 2 * spacingY;

        // Textfelder
        stackPriceField = new TextFieldWidget(this.textRenderer, inputX, topY, inputWidth, 20, Text.literal("Stackpreis"));
        stackPriceField.setPlaceholder(Text.literal("Stackpreis...").formatted(Formatting.GRAY));
        stackPriceField.setTextPredicate(s -> s.matches("[0-9]*"));
        this.addDrawableChild(stackPriceField);
        dkPriceField = new TextFieldWidget(this.textRenderer, inputX, topY + spacingY, inputWidth, 20, Text.literal("DK-Preis"));
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

        this.setInitialFocus(stackPriceField);
        updateConfirmingState();
    }

    // Startet Bestätigung
    private void startConfirmation() {
        if (this.client == null || this.client.player == null) return;
        String stackPriceStr = stackPriceField.getText(); String dkPriceStr = dkPriceField.getText();
        if (stackPriceStr.isEmpty() || dkPriceStr.isEmpty()) { setFeedback(Text.literal("Bitte beide Preise eingeben.").formatted(Formatting.RED), 3000); return; }
        try {
            int stackPrice = Integer.parseInt(stackPriceStr); int dkPrice = Integer.parseInt(dkPriceStr);
            if (stackPrice < 0 || dkPrice < 0) { setFeedback(Text.literal("Preise nicht negativ.").formatted(Formatting.RED), 3000); return; }
            this.confirmedStackPrice = stackPrice; this.confirmedDkPrice = dkPrice; this.confirmedItemName = Registries.ITEM.getId(itemToSubmit.getItem()).toString();
            this.confirming = true; updateConfirmingState();
            setFeedback(Text.literal("Sende Stack: " + stackPrice + "$, DK: " + dkPrice + "$?").formatted(Formatting.YELLOW), 10000);
        } catch (NumberFormatException e) { setFeedback(Text.literal("Ungültige Zahl.").formatted(Formatting.RED), 3000); }
    }

    // Führt API Call aus
    private void executeSubmit() {
        if (this.client == null || this.client.player == null) return;
        confirmButton.active = false; cancelButton.active = false; confirmButton.setMessage(Text.literal("Sende..."));
        setFeedback(Text.literal("Sende Preisvorschlag...").formatted(Formatting.YELLOW), 5000);
        String playerName = client.player.getName().getString(); String playerUuid = client.player.getUuidAsString();
        CompletableFuture<Boolean> future = TradeCore.apiClient.submitPriceSuggestion(confirmedItemName, confirmedStackPrice, confirmedDkPrice, playerName, playerUuid);
        future.thenAcceptAsync(success -> {
            if (success) {
                setFeedback(Text.literal("Preis erfolgreich gesendet!").formatted(Formatting.GREEN), 3000);
                stackPriceField.setText(""); dkPriceField.setText(""); this.confirming = false; updateConfirmingState();
            } else {
                setFeedback(Text.literal("Fehler beim Senden. Evtl. Zeitlimit? (Logs!)").formatted(Formatting.RED), 4000);
                this.confirming = false; updateConfirmingState();
            }
            confirmButton.setMessage(Text.literal("Bestätigen"));
        }, MinecraftClient.getInstance());
    }

    // Bricht Bestätigung ab
    private void cancelConfirmation() {
        this.confirming = false; updateConfirmingState(); setFeedback(null, 0);
    }

    // Aktualisiert Sichtbarkeit der Widgets
    private void updateConfirmingState() {
        boolean fieldsVisible = !confirming;
        stackPriceField.setVisible(fieldsVisible); dkPriceField.setVisible(fieldsVisible);
        initialSubmitButton.visible = fieldsVisible; initialSubmitButton.active = fieldsVisible;
        confirmButton.visible = confirming; confirmButton.active = confirming;
        cancelButton.visible = confirming; cancelButton.active = confirming;
        this.setInitialFocus(confirming ? confirmButton : stackPriceField);
    }

    // Setzt Feedback-Text
    private void setFeedback(Text message, long durationMillis) {
        this.feedbackText = message; this.feedbackTimeout = System.currentTimeMillis() + durationMillis;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        int itemX = this.width / 2 - 8; int itemY = this.height / 2 - 80;
        context.drawItem(itemToSubmit, itemX, itemY); // Item zeichnen
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF); // Titel

        // Labels nur im Eingabemodus
        if (!confirming) {
            int labelX = stackPriceField.getX() - 5 - this.textRenderer.getWidth("Stackpreis:");
            context.drawTextWithShadow(this.textRenderer, Text.literal("Stackpreis:"), labelX, stackPriceField.getY() + 6, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("DK-Preis:"), labelX, dkPriceField.getY() + 6, 0xFFFFFF);
        }

        super.render(context, mouseX, mouseY, delta); // Alle sichtbaren Widgets rendern

        // Feedback oder Hinweis unten zeichnen
        Text bottomText = null;
        if (feedbackText != null && System.currentTimeMillis() < feedbackTimeout) {
            bottomText = feedbackText; // Aktives Feedback
        } else {
            feedbackText = null;
            // Hinweis auf implizite Zustimmung (wenn kein anderes Feedback aktiv)
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