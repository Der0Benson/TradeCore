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
// import java.util.function.Consumer; // Nicht mehr direkt benötigt


public class PriceSubmissionScreen extends Screen {

    private final ItemStack itemToSubmit;
    private TextFieldWidget stackPriceField;
    private TextFieldWidget dkPriceField;
    private ButtonWidget submitButton;
    private Text feedbackText = null;
    private long feedbackTimeout = 0;

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
        int topY = this.height / 2 - 30; // Start Y für erstes Feld
        int spacingY = 30; // Vertikaler Abstand

        // Stackpreis Feld
        stackPriceField = new TextFieldWidget(this.textRenderer, inputX, topY, inputWidth, 20, Text.literal("Stackpreis"));
        stackPriceField.setPlaceholder(Text.literal("Stackpreis...").formatted(Formatting.GRAY));
        stackPriceField.setTextPredicate(s -> s.matches("[0-9]*")); // Nur Ziffern
        this.addDrawableChild(stackPriceField);

        // DK-Preis Feld
        dkPriceField = new TextFieldWidget(this.textRenderer, inputX, topY + spacingY, inputWidth, 20, Text.literal("DK-Preis"));
        dkPriceField.setPlaceholder(Text.literal("DK-Preis...").formatted(Formatting.GRAY));
        dkPriceField.setTextPredicate(s -> s.matches("[0-9]*")); // Nur Ziffern
        this.addDrawableChild(dkPriceField);

        // Submit Button
        submitButton = ButtonWidget.builder(Text.literal("Preis senden"), button -> submitPrice())
                .dimensions(centerX - 75, topY + 2 * spacingY, 150, 20)
                .build();
        this.addDrawableChild(submitButton);

        // Close Button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Schließen"), button -> this.close())
                .dimensions(centerX - 75, topY + 2 * spacingY + 24, 150, 20)
                .build());

        this.setInitialFocus(stackPriceField); // Fokus auf erstes Feld
    }

    private void submitPrice() {
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

            submitButton.active = false;
            submitButton.setMessage(Text.literal("Sende..."));
            setFeedback(Text.literal("Sende Preisvorschlag...").formatted(Formatting.YELLOW), 5000);

            // Item-ID sicher abrufen
            String itemName = Registries.ITEM.getId(itemToSubmit.getItem()).toString();
            String playerName = client.player.getName().getString();
            String playerUuid = client.player.getUuidAsString();

            CompletableFuture<Boolean> future = TradeCore.apiClient.submitPriceSuggestion(
                    itemName, stackPrice, dkPrice, playerName, playerUuid
            );

            // Callback im Minecraft-Thread ausführen, um GUI sicher zu aktualisieren
            future.thenAcceptAsync(success -> {
                if (success) {
                    setFeedback(Text.literal("Preis erfolgreich gesendet!").formatted(Formatting.GREEN), 3000);
                    stackPriceField.setText("");
                    dkPriceField.setText("");
                } else {
                    setFeedback(Text.literal("Fehler beim Senden des Preises. Siehe Logs.").formatted(Formatting.RED), 4000);
                }
                submitButton.active = true;
                submitButton.setMessage(Text.literal("Preis senden"));
            }, MinecraftClient.getInstance()); // Wichtig: Callback im Client-Thread

        } catch (NumberFormatException e) {
            setFeedback(Text.literal("Ungültige Zahl eingegeben.").formatted(Formatting.RED), 3000);
            submitButton.active = true; // Button wieder aktivieren bei Eingabefehler
            submitButton.setMessage(Text.literal("Preis senden"));
        }
    }

    private void setFeedback(Text message, long durationMillis) {
        this.feedbackText = message;
        this.feedbackTimeout = System.currentTimeMillis() + durationMillis;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta); // Hintergrund

        int itemX = this.width / 2 - 8;
        int itemY = this.height / 2 - 80; // Position für das Item oben
        context.drawItem(itemToSubmit, itemX, itemY);
        // context.drawItemTooltip(this.textRenderer, itemToSubmit, itemX, itemY); // Tooltip stört ggf.

        // Titel rendern
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        // Labels links neben die Felder zeichnen
        int labelX = stackPriceField.getX() - 5 - this.textRenderer.getWidth("Stackpreis:"); // X-Position für Labels
        context.drawTextWithShadow(this.textRenderer, Text.literal("Stackpreis:"), labelX, stackPriceField.getY() + 6, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("DK-Preis:"), labelX, dkPriceField.getY() + 6, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta); // Widgets rendern

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