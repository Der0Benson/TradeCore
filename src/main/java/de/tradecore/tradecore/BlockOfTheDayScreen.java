package de.tradecore.tradecore;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
// import net.minecraft.item.ItemStack; // Nicht mehr benötigt für diesen Zweck
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class BlockOfTheDayScreen extends Screen {

    private Text itemText = Text.literal("Laden...").formatted(Formatting.YELLOW);
    private Text gewinnText = Text.literal("Laden...").formatted(Formatting.YELLOW);
    private Text statusText = Text.literal("");

    private TextWidget itemDisplayWidget;
    private TextWidget gewinnDisplayWidget;
    private ButtonWidget createBdtButton;
    private ButtonWidget closeButton;

    protected BlockOfTheDayScreen() {
        super(Text.literal("Block des Tages"));
    }

    @Override
    protected void init() {
        super.init();
        if (this.client == null || TradeCore.apiClient == null) { /* ... Fehler ... */ return; }

        int screenWidth = this.width; int screenHeight = this.height; int centerX = screenWidth / 2;

        // Titel
        this.addDrawableChild(new TextWidget(0, 20, screenWidth, this.textRenderer.fontHeight, this.title, this.textRenderer).alignCenter());

        // Item-Anzeige
        itemDisplayWidget = new TextWidget(0, screenHeight / 2 - 20, screenWidth, this.textRenderer.fontHeight, itemText, this.textRenderer);
        itemDisplayWidget.alignCenter(); this.addDrawableChild(itemDisplayWidget);

        // Gewinn-Anzeige
        gewinnDisplayWidget = new TextWidget(0, screenHeight / 2 + 5, screenWidth, this.textRenderer.fontHeight, gewinnText, this.textRenderer);
        gewinnDisplayWidget.alignCenter(); this.addDrawableChild(gewinnDisplayWidget);

        // Button zum Erstellen (initial unsichtbar)
        int createButtonY = screenHeight / 2 + 30;
        createBdtButton = ButtonWidget.builder(Text.literal("Neuen BdT erstellen...").formatted(Formatting.GREEN), button -> {
                    // KEIN Item aus der Hand mehr nötig
                    if (this.client != null) {
                        // Öffne den CreateBdtScreen direkt
                        this.client.setScreen(new CreateBdtScreen(this)); // Übergibt nur Parent-Screen
                    }
                })
                .dimensions(centerX - 100, createButtonY, 200, 20)
                .build();
        createBdtButton.visible = false; this.addDrawableChild(createBdtButton);

        // Schließen Button
        int closeButtonY = screenHeight - 40;
        closeButton = ButtonWidget.builder(Text.literal("Schließen"), button -> this.close())
                .dimensions(centerX - 75, closeButtonY, 150, 20).build();
        this.addDrawableChild(closeButton);

        // Daten abrufen
        fetchDataAndUpdateScreen();
    }

    private void fetchDataAndUpdateScreen() {
        setFeedback(Text.literal("Lade Daten...").formatted(Formatting.YELLOW), 5000);
        if(createBdtButton != null) createBdtButton.visible = false;

        TradeCore.apiClient.fetchBlockOfTheDayAsync().thenAcceptAsync(result -> {
            if (result.found) {
                this.itemText = Text.literal("Item: ").append(Text.literal(result.itemName).formatted(Formatting.AQUA));
                this.gewinnText = Text.literal("Gewinn: ").append(Text.literal(result.gewinn).formatted(Formatting.GOLD));
                setFeedback(null, 0);
                if(createBdtButton != null) createBdtButton.visible = false;
            } else {
                this.itemText = Text.literal("-").formatted(Formatting.GRAY);
                this.gewinnText = Text.literal("-").formatted(Formatting.GRAY);
                setFeedback(Text.literal(result.message != null ? result.message : "Fehler").formatted(Formatting.RED), 0);
                if(createBdtButton != null) { createBdtButton.visible = true; }
                else { TradeCore.LOGGER.error("createBdtButton war null in Callback!"); }
            }
            if (itemDisplayWidget != null) itemDisplayWidget.setMessage(itemText);
            if (gewinnDisplayWidget != null) gewinnDisplayWidget.setMessage(gewinnText);

        }, MinecraftClient.getInstance());
    }

    // Hilfsmethode für Feedback/Status
    private void setFeedback(Text message, long durationMillis) {
        this.statusText = message != null ? message : Text.literal("");
        // Implementiere Timeout Logik hier, falls gewünscht
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        // Statusmeldung zeichnen
        if (!statusText.getString().isEmpty()) {
            int statusY = this.height / 2 + 60;
            if (createBdtButton != null && createBdtButton.visible) {
                statusY = createBdtButton.getY() + createBdtButton.getHeight() + 5;
            }
            if(closeButton != null) { statusY = Math.min(statusY, closeButton.getY() - this.textRenderer.fontHeight - 5); }
            context.drawCenteredTextWithShadow(this.textRenderer, statusText, this.width / 2, statusY, 0xFFFFFF);
        }
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() { if (this.client != null) this.client.setScreen(null); else super.close(); }
}