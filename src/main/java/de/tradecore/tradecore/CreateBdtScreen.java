package de.tradecore.tradecore;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
// import net.minecraft.item.ItemStack; // Nicht mehr benötigt
// import net.minecraft.registry.Registries; // Nicht mehr benötigt
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
// import net.minecraft.util.Identifier; // Nicht mehr benötigt für diese Validierung

import java.util.concurrent.CompletableFuture;

public class CreateBdtScreen extends Screen {

    // Textfeld für den Anzeigenamen des Items
    private TextFieldWidget itemDisplayNameField; // NEU: Umbenannt/Neues Feld
    private TextFieldWidget gewinnField;
    private ButtonWidget submitButton;
    private Text feedbackText = null;
    private long feedbackTimeout = 0;
    private final Screen parentScreen;

    protected CreateBdtScreen(Screen parent) {
        super(Text.literal("Neuen Block des Tages erstellen"));
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        super.init();
        if (this.client == null) return;

        int centerX = this.width / 2;
        int contentWidth = this.width - 80;
        int inputX = centerX - contentWidth / 2;
        int topY = this.height / 2 - 40;
        int spacingY = 25;

        // NEU: Textfeld für Item-Anzeigenamen
        itemDisplayNameField = new TextFieldWidget(this.textRenderer, inputX, topY, contentWidth, 20, Text.literal("Item Name (Anzeige)"));
        itemDisplayNameField.setPlaceholder(Text.literal("z.B. Diamantblock, Sandstein...").formatted(Formatting.GRAY));
        itemDisplayNameField.setMaxLength(250); // Passend zur DB Spalte 'item_name' (ggf. anpassen)
        this.addDrawableChild(itemDisplayNameField);

        // Gewinn Textfeld
        gewinnField = new TextFieldWidget(this.textRenderer, inputX, topY + spacingY + 5, contentWidth, 20, Text.literal("Gewinn-Beschreibung"));
        gewinnField.setPlaceholder(Text.literal("Beschreibung des Gewinns...").formatted(Formatting.GRAY));
        gewinnField.setMaxLength(250);
        this.addDrawableChild(gewinnField);

        // Submit Button
        submitButton = ButtonWidget.builder(Text.literal("BdT Erstellen"), button -> createBdtEntry())
                .dimensions(centerX - 100, topY + 2 * spacingY + 15, 98, 20)
                .build();
        this.addDrawableChild(submitButton);

        // Abbrechen/Zurück Button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Abbrechen"), button -> closeAndReturn())
                .dimensions(centerX + 2, topY + 2 * spacingY + 15, 98, 20)
                .build());

        this.setInitialFocus(itemDisplayNameField); // Fokus auf das erste Feld
    }

    private void createBdtEntry() {
        if (this.client == null || this.client.player == null || TradeCore.apiClient == null) return;

        String itemDisplayName = itemDisplayNameField.getText().trim(); // Lese Anzeigenamen
        String gewinnText = gewinnField.getText().trim();

        // Validierung
        if (itemDisplayName.isEmpty()) {
            setFeedback(Text.literal("Bitte einen Item-Namen eingeben.").formatted(Formatting.RED), 3000);
            return;
        }
        // Keine ID-Format-Validierung mehr nötig
        /*
        if (Identifier.tryParse(itemName) == null) {
             setFeedback(Text.literal("Ungültiges Item-ID Format...").formatted(Formatting.RED), 4000);
             return;
        }
        */

        if (gewinnText.isEmpty()) {
            setFeedback(Text.literal("Bitte eine Gewinn-Beschreibung eingeben.").formatted(Formatting.RED), 3000);
            return;
        }

        String playerUuid = client.player.getUuidAsString();

        submitButton.active = false;
        submitButton.setMessage(Text.literal("Sende..."));
        setFeedback(Text.literal("Erstelle BdT Eintrag...").formatted(Formatting.YELLOW), 5000);

        // Rufe API mit dem Anzeigenamen auf
        CompletableFuture<Boolean> future = TradeCore.apiClient.createBlockOfTheDayAsync(itemDisplayName, gewinnText, playerUuid);

        future.thenAcceptAsync(success -> {
            if (success) {
                setFeedback(Text.literal("Block des Tages erfolgreich erstellt!").formatted(Formatting.GREEN), 4000);
                net.minecraft.util.Util.getMainWorkerExecutor().execute(() -> {
                    try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                    if(this.client != null) { this.client.execute(this::closeAndReturn); }
                });
            } else {
                setFeedback(Text.literal("Fehler. Berechtigung? Bereits da? (Logs!)").formatted(Formatting.RED), 5000);
                submitButton.active = true;
                submitButton.setMessage(Text.literal("BdT Erstellen"));
            }
        }, MinecraftClient.getInstance());
    }

    private void setFeedback(Text message, long durationMillis) {
        this.feedbackText = message;
        this.feedbackTimeout = System.currentTimeMillis() + durationMillis;
    }

    private void closeAndReturn() {
        if (this.client != null) {
            this.client.execute(() -> this.client.setScreen(this.parentScreen));
        } else { this.close(); }
    }


    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        // Labels für Textfelder
        context.drawTextWithShadow(this.textRenderer, Text.literal("Item-Name:"), itemDisplayNameField.getX(), itemDisplayNameField.getY() - 10, 0xFFFFFF); // Angepasst
        context.drawTextWithShadow(this.textRenderer, Text.literal("Gewinn-Text:"), gewinnField.getX(), gewinnField.getY() - 10, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta); // Widgets

        // Feedback
        if (feedbackText != null && System.currentTimeMillis() < feedbackTimeout) {
            int feedbackY = this.height / 2 + 60; // Unter Buttons
            context.drawCenteredTextWithShadow(this.textRenderer, feedbackText, this.width / 2, feedbackY, 0xFFFFFF);
        } else { feedbackText = null; }
    }

    @Override
    public boolean shouldPause() { return true; }

    @Override
    public void close() { closeAndReturn(); }
}