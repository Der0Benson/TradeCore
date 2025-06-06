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
    private TextFieldWidget stueckPriceField;
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
    private int confirmedStueckPrice;
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
        int inputWidth = 150;
        int inputX = centerX - inputWidth / 2;
        int topY = this.height / 2 - 50;
        int spacingY = 25;
        int buttonY = topY + 3 * spacingY + 5;

        stueckPriceField = new TextFieldWidget(this.textRenderer, inputX, topY, inputWidth, 20, Text.literal("Stückpreis"));
        stueckPriceField.setPlaceholder(Text.literal("Stückpreis...").formatted(Formatting.GRAY));
        stueckPriceField.setTextPredicate(s -> s.matches("[0-9]*"));
        this.addDrawableChild(stueckPriceField);

        stackPriceField = new TextFieldWidget(this.textRenderer, inputX, topY + spacingY, inputWidth, 20, Text.literal("Stackpreis"));
        stackPriceField.setPlaceholder(Text.literal("Stackpreis...").formatted(Formatting.GRAY));
        stackPriceField.setTextPredicate(s -> s.matches("[0-9]*"));
        this.addDrawableChild(stackPriceField);

        dkPriceField = new TextFieldWidget(this.textRenderer, inputX, topY + 2 * spacingY, inputWidth, 20, Text.literal("DK-Preis"));
        dkPriceField.setPlaceholder(Text.literal("DK-Preis...").formatted(Formatting.GRAY));
        dkPriceField.setTextPredicate(s -> s.matches("[0-9]*"));
        this.addDrawableChild(dkPriceField);

        initialSubmitButton = ButtonWidget.builder(Text.literal("Preis prüfen & senden"), button -> startConfirmation())
                .dimensions(centerX - 75, buttonY, 150, 20).build();
        this.addDrawableChild(initialSubmitButton);

        confirmButton = ButtonWidget.builder(Text.literal("Bestätigen"), button -> executeSubmit())
                .dimensions(centerX - 75, buttonY, 70, 20).build();
        confirmButton.visible = false; this.addDrawableChild(confirmButton);
        cancelButton = ButtonWidget.builder(Text.literal("Abbrechen"), button -> cancelConfirmation())
                .dimensions(centerX + 5, buttonY, 70, 20).build();
        cancelButton.visible = false; this.addDrawableChild(cancelButton);

        closeButton = ButtonWidget.builder(Text.literal("Schließen"), button -> this.close())
                .dimensions(centerX - 75, buttonY + 24, 150, 20).build();
        this.addDrawableChild(closeButton);

        this.setInitialFocus(stueckPriceField);
        updateConfirmingState();
    }

    private void startConfirmation() {
        if (this.client == null || this.client.player == null) return;
        String stueckPriceStr = stueckPriceField.getText();
        String stackPriceStr = stackPriceField.getText();
        String dkPriceStr = dkPriceField.getText();

        if (stueckPriceStr.isEmpty() && stackPriceStr.isEmpty() && dkPriceStr.isEmpty()) {
            setFeedback(Text.literal("Bitte mindestens einen Preis eingeben.").formatted(Formatting.RED), 3000);
            return;
        }

        try {
            int stueckPrice = stueckPriceStr.isEmpty() ? 0 : Integer.parseInt(stueckPriceStr);
            int stackPrice = stackPriceStr.isEmpty() ? 0 : Integer.parseInt(stackPriceStr);
            int dkPrice = dkPriceStr.isEmpty() ? 0 : Integer.parseInt(dkPriceStr);

            if (stueckPrice < 0 || stackPrice < 0 || dkPrice < 0) {
                setFeedback(Text.literal("Preise dürfen nicht negativ sein.").formatted(Formatting.RED), 3000);
                return;
            }

            this.confirmedStueckPrice = stueckPrice;
            this.confirmedStackPrice = stackPrice;
            this.confirmedDkPrice = dkPrice;
            this.confirmedItemName = Registries.ITEM.getId(itemToSubmit.getItem()).toString();
            this.confirming = true;
            updateConfirmingState();

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

    private void executeSubmit() {
        if (this.client == null || this.client.player == null) return;
        confirmButton.active = false; cancelButton.active = false; confirmButton.setMessage(Text.literal("Sende..."));
        setFeedback(Text.literal("Sende Preisvorschlag...").formatted(Formatting.YELLOW), 5000);
        String playerName = client.player.getName().getString(); String playerUuid = client.player.getUuidAsString();

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
                stueckPriceField.setText("");
                stackPriceField.setText("");
                dkPriceField.setText("");
                this.confirming = false;
                updateConfirmingState();
            } else {
                setFeedback(Text.literal("Fehler beim Senden. Evtl. Zeitlimit? (Logs!)").formatted(Formatting.RED), 4000);
                this.confirming = false;
                updateConfirmingState();
            }
            client.execute(() -> {
                confirmButton.setMessage(Text.literal("Bestätigen"));
            });
        }, MinecraftClient.getInstance());
    }

    private void cancelConfirmation() {
        this.confirming = false; updateConfirmingState(); setFeedback(null, 0);
    }

    private void updateConfirmingState() {
        boolean fieldsVisible = !confirming;
        stueckPriceField.setVisible(fieldsVisible);
        stackPriceField.setVisible(fieldsVisible);
        dkPriceField.setVisible(fieldsVisible);
        initialSubmitButton.visible = fieldsVisible;
        initialSubmitButton.active = fieldsVisible;

        confirmButton.visible = confirming;
        confirmButton.active = confirming;
        cancelButton.visible = confirming;
        cancelButton.active = confirming;

        this.setInitialFocus(confirming ? confirmButton : stueckPriceField);
    }

    private void setFeedback(Text message, long durationMillis) {
        this.feedbackText = message; this.feedbackTimeout = System.currentTimeMillis() + durationMillis;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 1. Hintergrund zeichnen
        this.renderBackground(context, mouseX, mouseY, delta);

        // 2. Titel zeichnen
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        // 3. Labels nur im Eingabemodus (vor den Widgets, aber nach dem Titel)
        if (!confirming) {
            int labelXOffset = 5;
            int stueckLabelX = stueckPriceField.getX() - this.textRenderer.getWidth("Stückpreis:") - labelXOffset;
            context.drawTextWithShadow(this.textRenderer, Text.literal("Stückpreis:"), stueckLabelX, stueckPriceField.getY() + 6, 0xFFFFFF);

            int stackLabelX = stackPriceField.getX() - this.textRenderer.getWidth("Stackpreis:") - labelXOffset;
            context.drawTextWithShadow(this.textRenderer, Text.literal("Stackpreis:"), stackLabelX, stackPriceField.getY() + 6, 0xFFFFFF);

            int dkLabelX = dkPriceField.getX() - this.textRenderer.getWidth("DK-Preis:") - labelXOffset;
            context.drawTextWithShadow(this.textRenderer, Text.literal("DK-Preis:"), dkLabelX, dkPriceField.getY() + 6, 0xFFFFFF);
        }

        // 4. Widgets (Buttons, Textfelder) zeichnen - WICHTIG: `super.render` zeichnet diese.
        super.render(context, mouseX, mouseY, delta);

        // 5. Skaliertes Item-Preview ZEICHNEN (NACH `super.render`, um über Widgets zu sein)
        //    Die Y-Position ist so gewählt, dass es knapp über den ersten Eingabefeldern sitzt.
        float itemScaleFactor = 3.0f;
        float originalItemGuiSize = 16.0f;
        float scaledItemSize = originalItemGuiSize * itemScaleFactor;
        float itemRenderX = (this.width - scaledItemSize) / 2.0f;
        float itemRenderY = this.height / 2 - 100; // Y-Position für die Oberkante des Items
        float itemZOffset = 100.0f; // Z-Offset, um es "nach vorne" zu bringen (über andere Elemente mit Z=0)

        context.getMatrices().push();
        // Verschiebe zu (X, Y) und füge einen Z-Offset hinzu, um es vor andere GUI-Elemente zu bringen
        context.getMatrices().translate(itemRenderX, itemRenderY, itemZOffset);
        context.getMatrices().scale(itemScaleFactor, itemScaleFactor, 1.0f);
        // Zeichne das Item an der lokalen Position (0,0) des transformierten Matrix-Stacks
        context.drawItem(itemToSubmit, 0, 0);
        context.getMatrices().pop();


        // 6. Feedback-Text (ganz obenauf)
        Text bottomText = null;
        if (feedbackText != null && System.currentTimeMillis() < feedbackTimeout) {
            bottomText = feedbackText;
        } else {
            feedbackText = null;
            bottomText = Text.literal("Hinweis: Absenden nutzt UUID zur Spam-Verhinderung.")
                    .formatted(Formatting.DARK_GRAY, Formatting.ITALIC);
        }
        if (bottomText != null) {
            // Feedback-Text etwas höher positionieren, falls das Item jetzt mehr Platz braucht
            // oder wenn es über dem Item erscheinen soll (dann Z-Offset für Text anpassen oder Text nach Item zeichnen)
            int feedbackY = closeButton.getY() + closeButton.getHeight() + 5; // Unter dem Schließen-Button
            if (feedbackY + textRenderer.fontHeight > this.height -5) { // Verhindern, dass es aus dem Screen geht
                feedbackY = this.height - this.textRenderer.fontHeight - 5;
            }
            // Feedback Text mit Z-Offset zeichnen, um sicherzustellen, dass er über dem Item ist, falls sie überlappen
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 200.0f); // Noch höherer Z-Offset für Feedback-Text
            context.drawCenteredTextWithShadow(this.textRenderer, bottomText, this.width / 2, feedbackY, 0xFFFFFF);
            context.getMatrices().pop();
        }
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() { if (this.client != null) this.client.setScreen(null); else super.close(); }
}