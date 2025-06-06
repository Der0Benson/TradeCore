package de.tradecore.tradecore;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.util.NarratorManager;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.awt.Color; // Für Farbinterpolierung

public class UserLevelScreen extends Screen {

    private final Screen parentScreen;
    private boolean isLoading = true;
    private PriceAPIClient.UserLevelResult levelData;

    private TextWidget playerNameText;
    private TextWidget levelText;
    private TextWidget xpText;
    private Text statusText; // Für Lade-/Fehlermeldungen

    private static final int PROGRESS_BAR_WIDTH = 200;
    private static final int PROGRESS_BAR_HEIGHT = 10;
    private static final int PROGRESS_BAR_COLOR_EMPTY = new Color(50, 50, 50, 200).getRGB(); // Dunkelgrau, halbtransparent
    private static final int PROGRESS_BAR_COLOR_FILLED_START = new Color(0, 255, 0, 220).getRGB(); // Grün
    private static final int PROGRESS_BAR_COLOR_FILLED_END = new Color(120, 255, 120, 220).getRGB(); // Hellgrün

    public UserLevelScreen(Screen parent) {
        super(Text.literal("Level-Anzeige").formatted(Formatting.BOLD));
        this.parentScreen = parent;
        this.statusText = Text.literal("Lade Level-Daten...").formatted(Formatting.YELLOW);
    }

    @Override
    protected void init() {
        super.init();
        if (this.client == null || this.client.player == null || TradeCore.apiClient == null) {
            this.statusText = Text.literal("Fehler: Client oder Spieler nicht verfügbar.").formatted(Formatting.RED);
            isLoading = false;
            addBackButton();
            return;
        }

        // Initialisiere Text-Widgets (werden später aktualisiert)
        int screenWidth = this.width;
        int screenHeight = this.height;
        int centerX = screenWidth / 2;

        playerNameText = new TextWidget(0, screenHeight / 2 - 60, screenWidth, this.textRenderer.fontHeight, NarratorManager.EMPTY, this.textRenderer);
        playerNameText.alignCenter();
        this.addDrawableChild(playerNameText);

        levelText = new TextWidget(0, screenHeight / 2 - 40, screenWidth, this.textRenderer.fontHeight, NarratorManager.EMPTY, this.textRenderer);
        levelText.alignCenter();
        this.addDrawableChild(levelText);

        xpText = new TextWidget(0, screenHeight / 2 + 5, screenWidth, this.textRenderer.fontHeight, NarratorManager.EMPTY, this.textRenderer); // Unter dem Balken
        xpText.alignCenter();
        this.addDrawableChild(xpText);

        addBackButton();
        fetchLevelData();
    }

    private void addBackButton() {
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Zurück"), button -> {
                    if (this.client != null) {
                        this.client.setScreen(this.parentScreen);
                    }
                })
                .dimensions(this.width / 2 - 100, this.height - 40, 200, 20)
                .build());
    }

    private void fetchLevelData() {
        isLoading = true;
        this.statusText = Text.literal("Lade Level-Daten...").formatted(Formatting.YELLOW);
        updateTextWidgets(); // Um "Laden..." etc. sofort anzuzeigen

        if (this.client == null || this.client.player == null || TradeCore.apiClient == null) {
            this.statusText = Text.literal("Fehler: Client oder Spieler nicht verfügbar für API Aufruf.").formatted(Formatting.RED);
            isLoading = false;
            updateTextWidgets();
            return;
        }
        String playerUuid = this.client.player.getUuidAsString();

        TradeCore.apiClient.fetchUserLevelAsync(playerUuid).whenCompleteAsync((result, throwable) -> {
            isLoading = false;
            if (throwable != null) {
                TradeCore.LOGGER.error("Fehler beim Abrufen der User Level Daten:", throwable);
                this.levelData = null;
                this.statusText = Text.literal("Fehler beim Laden der Level-Daten.").formatted(Formatting.RED);
            } else if (result != null) {
                if (result.success) {
                    this.levelData = result;
                    this.statusText = Text.literal(""); // Keine Fehlermeldung
                } else {
                    this.levelData = null;
                    this.statusText = Text.literal(result.message != null ? result.message : "Level-Daten nicht gefunden.").formatted(Formatting.RED);
                }
            } else {
                this.levelData = null;
                this.statusText = Text.literal("Unbekannter Fehler beim Laden der Level-Daten.").formatted(Formatting.RED);
            }
            updateTextWidgets();
        }, MinecraftClient.getInstance()); // Wichtig: Ausführung im Minecraft-Client-Thread
    }

    private void updateTextWidgets() {
        if (isLoading) {
            playerNameText.setMessage(Text.literal(""));
            levelText.setMessage(Text.literal("Laden...").formatted(Formatting.YELLOW));
            xpText.setMessage(Text.literal(""));
        } else if (levelData != null && levelData.success) {
            playerNameText.setMessage(Text.literal(levelData.playerName != null ? levelData.playerName : "Spieler").formatted(Formatting.GOLD, Formatting.BOLD));
            levelText.setMessage(Text.literal("Level: ").formatted(Formatting.AQUA)
                    .append(Text.literal(String.valueOf(levelData.level)).formatted(Formatting.GREEN, Formatting.BOLD)));
            xpText.setMessage(Text.literal(String.format("%d / %d XP", levelData.currentXp, levelData.xpForNextLevel)).formatted(Formatting.GRAY));
        } else {
            playerNameText.setMessage(Text.literal(""));
            levelText.setMessage(statusText); // Zeigt hier die Fehlermeldung oder "Nicht gefunden" an
            xpText.setMessage(Text.literal(""));
        }
    }


    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta); // Standard-Hintergrund

        // Titel
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        // Zeichne Text-Widgets (werden von super.render() gehandhabt)
        super.render(context, mouseX, mouseY, delta);

        // Fortschrittsbalken zeichnen, wenn Daten vorhanden sind
        if (!isLoading && levelData != null && levelData.success) {
            int barX = this.width / 2 - PROGRESS_BAR_WIDTH / 2;
            int barY = this.height / 2 - PROGRESS_BAR_HEIGHT / 2 - 10; // Positioniere über dem XP-Text

            // Hintergrund des Balkens
            context.fill(barX, barY, barX + PROGRESS_BAR_WIDTH, barY + PROGRESS_BAR_HEIGHT, PROGRESS_BAR_COLOR_EMPTY);

            // Vordergrund (gefüllter Teil)
            float progress = 0.0f;
            if (levelData.xpForNextLevel > 0) { // Vermeide Division durch Null
                progress = (float) levelData.currentXp / levelData.xpForNextLevel;
            }
            progress = Math.max(0.0f, Math.min(progress, 1.0f)); // Stelle sicher, dass der Wert zwischen 0 und 1 liegt

            int filledWidth = (int) (PROGRESS_BAR_WIDTH * progress);

            // Farbverlauf für den gefüllten Balken
            Color start = new Color(PROGRESS_BAR_COLOR_FILLED_START, true);
            Color end = new Color(PROGRESS_BAR_COLOR_FILLED_END, true);

            for (int i = 0; i < filledWidth; i++) {
                float ratio = (float) i / PROGRESS_BAR_WIDTH; // Ratio basierend auf der Gesamtbreite
                int r = (int) (start.getRed() * (1 - ratio) + end.getRed() * ratio);
                int g = (int) (start.getGreen() * (1 - ratio) + end.getGreen() * ratio);
                int b = (int) (start.getBlue() * (1 - ratio) + end.getBlue() * ratio);
                int a = (int) (start.getAlpha() * (1 - ratio) + end.getAlpha() * ratio);
                context.fill(barX + i, barY, barX + i + 1, barY + PROGRESS_BAR_HEIGHT, new Color(r,g,b,a).getRGB());
            }


            // Rand des Balkens (optional)
            context.drawBorder(barX -1 , barY -1, PROGRESS_BAR_WIDTH + 2, PROGRESS_BAR_HEIGHT + 2, 0xFFFFFFFF); // Weißer Rand
        } else if (!isLoading) {
            // Falls keine Daten geladen werden konnten und es keine Ladeanzeige mehr gibt,
            // wird die Statusmeldung (Fehler etc.) bereits durch das levelText Widget angezeigt.
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parentScreen);
        } else {
            super.close();
        }
    }

    @Override
    public boolean shouldPause() {
        return false; // Das Spiel im Hintergrund nicht pausieren
    }
}