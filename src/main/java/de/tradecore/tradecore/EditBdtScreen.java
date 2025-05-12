package de.tradecore.tradecore;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class EditBdtScreen extends Screen {

    private final BlockOfTheDayScreen parentScreen;
    private final String bdtId;
    private final String currentItemName;
    private final String currentGewinn;
    private TextFieldWidget itemDisplayNameField;
    private TextFieldWidget gewinnField;
    private ButtonWidget saveButton;
    private ButtonWidget cancelButton;
    private ButtonWidget confirmButton;
    private ButtonWidget cancelConfirmButton;
    private Text feedbackText = null;
    private long feedbackTimeout = 0;
    private boolean showConfirmation = false;

    protected EditBdtScreen(BlockOfTheDayScreen parent, String bdtId, String currentItemName, String currentGewinn) {
        super(Text.literal("BdT bearbeiten"));
        this.parentScreen = parent;
        this.bdtId = bdtId;
        this.currentItemName = currentItemName;
        this.currentGewinn = currentGewinn;
    }

    @Override
    protected void init() {
        super.init();
        if (this.client == null) return;

        int centerX = this.width / 2;
        int contentWidth = this.width - 80;
        int inputX = centerX - contentWidth / 2;
        int topY = this.height / 2 - 60;
        int spacingY = 25;

        // Textfelder für Item-Name und Gewinn (vorbefüllt mit den aktuellen Daten)
        itemDisplayNameField = new TextFieldWidget(this.textRenderer, inputX, topY, contentWidth, 20, Text.literal("Item Name"));
        itemDisplayNameField.setText(currentItemName);
        itemDisplayNameField.setMaxLength(100);
        this.addDrawableChild(itemDisplayNameField);

        gewinnField = new TextFieldWidget(this.textRenderer, inputX, topY + spacingY + 5, contentWidth, 20, Text.literal("Gewinn"));
        gewinnField.setText(currentGewinn);
        gewinnField.setMaxLength(50);
        this.addDrawableChild(gewinnField);

        // Buttons zum Speichern und Abbrechen
        saveButton = ButtonWidget.builder(Text.literal("Speichern"), button -> {
                    String itemDisplayName = itemDisplayNameField.getText().trim();
                    String gewinnText = gewinnField.getText().trim();
                    if (itemDisplayName.isEmpty() || gewinnText.isEmpty()) {
                        setFeedback(Text.literal("Bitte alle Felder ausfüllen.").formatted(Formatting.RED), 3000);
                        return;
                    }
                    showConfirmation = true; // Show confirmation dialog
                })
                .dimensions(centerX - 100, topY + 2 * spacingY + 15, 98, 20)
                .build();
        this.addDrawableChild(saveButton);

        cancelButton = ButtonWidget.builder(Text.literal("Abbrechen"), button -> closeAndReturn())
                .dimensions(centerX + 2, topY + 2 * spacingY + 15, 98, 20)
                .build();
        this.addDrawableChild(cancelButton);

        // Confirmation dialog buttons (hidden initially)
        confirmButton = ButtonWidget.builder(Text.literal("Bestätigen"), button -> {
                    showConfirmation = false;
                    saveChanges();
                })
                .dimensions(centerX - 100, topY + 3 * spacingY + 15, 98, 20)
                .build();
        confirmButton.active = false;
        this.addDrawableChild(confirmButton);

        cancelConfirmButton = ButtonWidget.builder(Text.literal("Abbrechen"), button -> {
                    showConfirmation = false;
                })
                .dimensions(centerX + 2, topY + 3 * spacingY + 15, 98, 20)
                .build();
        cancelConfirmButton.active = false;
        this.addDrawableChild(cancelConfirmButton);

        // Fokus auf das erste Textfeld setzen
        this.setInitialFocus(itemDisplayNameField);
    }

    private void saveChanges() {
        if (this.client == null || TradeCore.apiClient == null) {
            setFeedback(Text.literal("Fehler: Ungültiger Zustand").formatted(Formatting.RED), 3000);
            return;
        }

        String itemDisplayName = itemDisplayNameField.getText().trim();
        String gewinnText = gewinnField.getText().trim();

        // Validierung (bereits in saveButton geprüft, aber für Sicherheit)
        if (itemDisplayName.isEmpty() || gewinnText.isEmpty()) {
            setFeedback(Text.literal("Bitte alle Felder ausfüllen.").formatted(Formatting.RED), 3000);
            return;
        }

        saveButton.active = false;
        setFeedback(Text.literal("Sende Änderungen...").formatted(Formatting.YELLOW), 3000);

        // API-Aufruf zum Aktualisieren des BdT
        TradeCore.apiClient.updateBlockOfTheDayAsync(this.bdtId, itemDisplayName, gewinnText)
                .thenAcceptAsync(result -> {
                    saveButton.active = true;
                    if (result.success) {
                        setFeedback(Text.literal("BdT erfolgreich aktualisiert!").formatted(Formatting.GREEN), 4000);
                        this.client.execute(() -> {
                            try {
                                Thread.sleep(1500);
                            } catch (InterruptedException ignored) {
                            }
                            this.closeAndReturn();
                        });
                    } else {
                        setFeedback(Text.literal(result.message).formatted(Formatting.RED), 5000);
                    }
                }, MinecraftClient.getInstance())
                .exceptionally(throwable -> {
                    this.client.execute(() -> {
                        saveButton.active = true;
                        setFeedback(Text.literal("Fehler: " + throwable.getMessage()).formatted(Formatting.RED), 5000);
                    });
                    return null;
                });
    }

    private void setFeedback(Text message, long durationMillis) {
        this.feedbackText = message;
        this.feedbackTimeout = System.currentTimeMillis() + durationMillis;
    }

    private void closeAndReturn() {
        if (this.client != null) {
            this.client.execute(() -> this.client.setScreen(this.parentScreen));
        } else {
            this.close();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Item-Name:"), itemDisplayNameField.getX(), itemDisplayNameField.getY() - 10, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Gewinn-Text:"), gewinnField.getX(), gewinnField.getY() - 10, 0xFFFFFF);
        if (showConfirmation) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Änderungen speichern?"), this.width / 2, gewinnField.getY() + 40, 0xFFFFFF);
            confirmButton.active = true;
            cancelConfirmButton.active = true;
        } else {
            confirmButton.active = false;
            cancelConfirmButton.active = false;
        }
        super.render(context, mouseX, mouseY, delta);
        if (feedbackText != null && System.currentTimeMillis() < feedbackTimeout) {
            int feedbackY = this.height / 2 + 80;
            context.drawCenteredTextWithShadow(this.textRenderer, feedbackText, this.width / 2, feedbackY, 0xFFFFFF);
        } else {
            feedbackText = null;
        }
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    @Override
    public void close() {
        closeAndReturn();
    }
}