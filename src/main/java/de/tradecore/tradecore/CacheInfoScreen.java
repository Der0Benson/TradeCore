package de.tradecore.tradecore;

// import com.mojang.blaze3d.systems.RenderSystem; // Nicht mehr benötigt für diesen Test
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
// import net.minecraft.client.render.BufferBuilder; // Nicht mehr benötigt für diesen Test
// import net.minecraft.client.render.GameRenderer; // Nicht mehr benötigt für diesen Test
// import net.minecraft.client.render.Tessellator; // Nicht mehr benötigt für diesen Test
// import net.minecraft.client.render.VertexFormat; // Nicht mehr benötigt für diesen Test
// import net.minecraft.client.render.VertexFormats; // Nicht mehr benötigt für diesen Test
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
// import org.joml.Matrix4f; // Nicht mehr benötigt für diesen Test

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class CacheInfoScreen extends Screen {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault());

    // private static final Identifier LOGO_TEXTURE = Identifier.of(TradeCore.MOD_ID, "textures/gui/tradecore_logo_color.png"); // Auskommentiert
    private static final int TEXTURE_WIDTH = 180;
    private static final int TEXTURE_HEIGHT = 35;

    private Text feedbackText = null;
    private long feedbackTimeout = 0;
    private ButtonWidget priceToggleButton;
    private ButtonWidget updateButton;
    private ButtonWidget impressumButton;
    private ButtonWidget wikiButton;
    private ButtonWidget bewerbenButton;
    private ButtonWidget modEnableToggleButton;
    private ButtonWidget userLevelButton;

    private final int buttonSpacing = 25;
    private final int linkButtonSpacing = 5;

    private static final long UPDATE_COOLDOWN_MILLIS = TimeUnit.HOURS.toMillis(1);

    private final String[] flyingNamesArray = {"Der_Wolfo_", "Der_Benson_", "Juleszlive"};
    private List<FlyingText> activeFlyingTexts;
    private Random random = new Random();

    public CacheInfoScreen() {
        super(Text.literal("TradeCore Info & Links").formatted(Formatting.BOLD));
        activeFlyingTexts = new ArrayList<>();
    }

    private Text getPriceToggleText() {
        return Text.literal("Preisanzeige: " + (TradeCoreConfig.showPricesOnlyOnShift ? "Nur bei Shift" : "Immer"));
    }

    private Text getModEnableToggleText() {
        return Text.literal("Mod Status: ").append(
                TradeCoreConfig.modEnabled ?
                        Text.literal("Aktiviert").formatted(Formatting.GREEN) :
                        Text.literal("Deaktiviert").formatted(Formatting.RED)
        );
    }

    @Override
    protected void init() {
        super.init();
        if (this.client == null) return;

        int screenWidth = this.width;
        int centerX = screenWidth / 2;
        int mainButtonWidth = 180;
        int linkButtonWidth = 200;

        // Angepasste Y-Positionierung ohne Logo-Höhe, da Logo auskommentiert
        int logoDisplayHeight = 0; // Da Logo auskommentiert
        int titleEstimatedHeight = this.textRenderer.fontHeight;
        int verticalOffset = 25;
        // StartY etwas höher, da das Logo fehlt
        int currentY = 10 + 5 + titleEstimatedHeight + 10 + verticalOffset;


        updateButton = ButtonWidget.builder(Text.literal("Preise jetzt aktualisieren"), button -> {
                    long lastTrigger = TradeCoreConfig.lastManualFetchTimestamp;
                    long currentTime = System.currentTimeMillis();
                    long timeSinceLast = currentTime - lastTrigger;

                    if (timeSinceLast > UPDATE_COOLDOWN_MILLIS || lastTrigger == 0) {
                        if (TradeCore.apiClient != null) {
                            TradeCore.apiClient.triggerPriceUpdate();
                            setFeedback(Text.literal("Preis-Aktualisierung gestartet...").formatted(Formatting.GREEN), 4000);
                        } else {
                            setFeedback(Text.literal("Fehler: API Client nicht initialisiert.").formatted(Formatting.RED), 3000);
                        }
                    } else {
                        long remainingMillis = UPDATE_COOLDOWN_MILLIS - timeSinceLast;
                        long remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis);
                        if (TimeUnit.MILLISECONDS.toSeconds(remainingMillis) % 60 > 0) remainingMinutes++;
                        remainingMinutes = Math.max(1, remainingMinutes);
                        setFeedback(Text.literal("Bitte warte noch ca. " + remainingMinutes + " Min.").formatted(Formatting.YELLOW), 3000);
                    }
                }).dimensions(centerX - mainButtonWidth / 2, currentY, mainButtonWidth, 20)
                .build();
        this.addDrawableChild(updateButton);
        currentY += buttonSpacing;

        priceToggleButton = this.addDrawableChild(ButtonWidget.builder(getPriceToggleText(), button -> {
                    TradeCoreConfig.showPricesOnlyOnShift = !TradeCoreConfig.showPricesOnlyOnShift;
                    button.setMessage(getPriceToggleText());
                    TradeCoreConfig.saveConfig();
                }).dimensions(centerX - mainButtonWidth / 2, currentY, mainButtonWidth, 20)
                .build());
        currentY += buttonSpacing;

        modEnableToggleButton = this.addDrawableChild(ButtonWidget.builder(getModEnableToggleText(), button -> {
                    TradeCoreConfig.toggleModEnabled();
                    button.setMessage(getModEnableToggleText());
                }).dimensions(centerX - mainButtonWidth / 2, currentY, mainButtonWidth, 20)
                .build());
        currentY += buttonSpacing;

        userLevelButton = ButtonWidget.builder(Text.literal("Level-Anzeige"), button -> {
                    if (this.client != null) {
                        this.client.setScreen(new UserLevelScreen(this));
                    }
                })
                .dimensions(centerX - mainButtonWidth / 2, currentY, mainButtonWidth, 20)
                .build();
        this.addDrawableChild(userLevelButton);
        currentY += buttonSpacing + 10;

        impressumButton = ButtonWidget.builder(Text.literal("Impressum").formatted(Formatting.GOLD), button -> openLink("https://mc-tradecore.de/Impressum.php", "Impressum"))
                .dimensions(centerX - linkButtonWidth / 2, currentY, linkButtonWidth, 20)
                .build();
        this.addDrawableChild(impressumButton);
        currentY += 20 + linkButtonSpacing;

        wikiButton = ButtonWidget.builder(Text.literal("Wiki").formatted(Formatting.AQUA), button -> openLink("https://mc-tradecore.de/html/wiki.php", "Wiki"))
                .dimensions(centerX - linkButtonWidth / 2, currentY, linkButtonWidth, 20)
                .build();
        this.addDrawableChild(wikiButton);
        currentY += 20 + linkButtonSpacing;

        bewerbenButton = ButtonWidget.builder(Text.literal("Bewerben").formatted(Formatting.GREEN), button -> openLink("https://mc-tradecore.de/Bewerbung.php", "Bewerben"))
                .dimensions(centerX - linkButtonWidth / 2, currentY, linkButtonWidth, 20)
                .build();
        this.addDrawableChild(bewerbenButton);

        int closeButtonY = this.height - 30;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Schließen"), button -> this.close())
                .dimensions(centerX - mainButtonWidth / 2, closeButtonY, mainButtonWidth, 20)
                .build());

        activeFlyingTexts.clear();
        for (String name : flyingNamesArray) {
            if (!isNameActive(name)) {
                spawnFlyingText(name);
            }
        }
    }

    private boolean isNameActive(String name) {
        for (FlyingText ft : activeFlyingTexts) {
            if (ft.text.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private void openLink(String url, String linkName) {
        try {
            Util.getOperatingSystem().open(new URI(url));
        } catch (URISyntaxException uriEx) {
            TradeCore.LOGGER.error("Ungültige URL-Syntax für Link '{}': {}", linkName, url, uriEx);
            setFeedback(Text.literal("Fehler: Link-Adresse ist fehlerhaft.").formatted(Formatting.RED), 3000);
            if (client != null && client.player != null) {
                client.player.sendMessage(Text.literal("Fehler: Die Link-Adresse für '" + linkName + "' scheint fehlerhaft zu sein.").formatted(Formatting.RED), false);
            }
        } catch (Exception e) {
            TradeCore.LOGGER.error("Unerwarteter Fehler beim Öffnen des Links '{}': {}", linkName, url, e);
            setFeedback(Text.literal("Link konnte nicht geöffnet werden. Details im Log.").formatted(Formatting.RED), 3000);
            if (client != null && client.player != null) {
                client.player.sendMessage(Text.literal("Ein unerwarteter Fehler ist beim Öffnen des Links '" + linkName + "' aufgetreten. Bitte prüfe die Logs.").formatted(Formatting.RED), false);
            }
        }
    }

    private void spawnFlyingText(String text) {
        if (this.client == null || this.textRenderer == null) return;
        float x = random.nextFloat() * this.width;
        float y = random.nextFloat() * this.height;
        float dx = (random.nextFloat() - 0.5f) * 1.5f;
        float dy = (random.nextFloat() - 0.5f) * 1.5f;
        if (dx == 0 && dy == 0) {
            dx = (random.nextBoolean() ? 0.5f : -0.5f);
            dy = (random.nextBoolean() ? 0.5f : -0.5f);
        }
        activeFlyingTexts.add(new FlyingText(text, x, y, dx, dy, 0xFFFFFF, this.textRenderer.getWidth(text)));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.client == null || this.textRenderer == null) return;

        if (activeFlyingTexts.size() < flyingNamesArray.length * 1.5 && random.nextInt(40) == 0) {
            spawnFlyingText(flyingNamesArray[random.nextInt(flyingNamesArray.length)]);
        }

        List<FlyingText> toRemove = new ArrayList<>();
        for (FlyingText ft : activeFlyingTexts) {
            ft.update(this.width, this.height, this.client.textRenderer.fontHeight);
            if (ft.isOutOfBounds(this.width, this.height)) {
                toRemove.add(ft);
            }
        }
        activeFlyingTexts.removeAll(toRemove);
    }

    private void setFeedback(Text message, long durationMillis) {
        this.feedbackText = message;
        this.feedbackTimeout = System.currentTimeMillis() + durationMillis;
    }

    private int getRainbowColor(float time) {
        float frequency = 0.1f;
        int red   = (int) (Math.sin(frequency * time + 0) * 127 + 128);
        int green = (int) (Math.sin(frequency * time + 2 * Math.PI / 3) * 127 + 128);
        int blue  = (int) (Math.sin(frequency * time + 4 * Math.PI / 3) * 127 + 128);
        return (red << 16) | (green << 8) | blue;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        // Logo-Zeichnung auskommentiert
        // int logoDisplayWidth = Math.min(TEXTURE_WIDTH, (int) (this.width * 0.6f));
        // int logoDisplayHeight = (int) ((float) logoDisplayWidth / TEXTURE_WIDTH * TEXTURE_HEIGHT);
        // int logoX = this.width / 2 - logoDisplayWidth / 2;
        // int logoY = 10;
        // context.draw... (LOGO HIER AUSKOMMENTIERT)

        // Titel - Y-Position angepasst, da Logo fehlt
        int titleY = 20; // Feste Y-Position für den Titel, da Logo fehlt
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, titleY, 0xFFFFFF);

        String lastUpdateText;
        if (TradeCore.apiClient != null) {
            long lastUpdate = TradeCore.apiClient.getLastUpdateTimestamp();
            if (lastUpdate == 0) {
                lastUpdateText = "Keine lokalen Preisdaten vorhanden.";
            } else {
                try {
                    lastUpdateText = "Preisdaten von: " + FORMATTER.format(Instant.ofEpochSecond(lastUpdate));
                } catch (Exception e) {
                    lastUpdateText = "Preisdaten vorhanden (Formatierungsfehler)";
                }
            }
        } else {
            lastUpdateText = "Fehler: API Client nicht initialisiert.";
        }
        int infoY = titleY + this.textRenderer.fontHeight + 5;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(lastUpdateText).formatted(Formatting.YELLOW), this.width / 2, infoY, 0xFFFFFF);

        for (FlyingText ft : activeFlyingTexts) {
            int rainbowColor = getRainbowColor(ft.colorTime);
            context.drawTextWithShadow(this.textRenderer, ft.text, (int) ft.x, (int) ft.y, rainbowColor);
        }

        super.render(context, mouseX, mouseY, delta); // Wichtig: Zeichnet die Buttons und andere Widgets

        if (updateButton != null && updateButton.isHovered()) {
            long lastTrigger = TradeCoreConfig.lastManualFetchTimestamp;
            long currentTime = System.currentTimeMillis();
            long timeSinceLast = currentTime - lastTrigger;
            boolean onCooldown = timeSinceLast <= UPDATE_COOLDOWN_MILLIS && lastTrigger != 0;

            if (onCooldown) {
                long remainingMillis = UPDATE_COOLDOWN_MILLIS - timeSinceLast;
                long remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis);
                if (TimeUnit.MILLISECONDS.toSeconds(remainingMillis) % 60 > 0) remainingMinutes++;
                remainingMinutes = Math.max(1, remainingMinutes);
                context.drawTooltip(this.textRenderer,
                        Text.literal("Cooldown: ca. " + remainingMinutes + " Min. übrig"),
                        mouseX + 8, mouseY + 8);
            }
        }

        if (feedbackText != null && System.currentTimeMillis() < feedbackTimeout) {
            int feedbackY = this.height - 15 - this.textRenderer.fontHeight;
            ButtonWidget closeButton = null;
            for(var child : children()) {
                if(child instanceof ButtonWidget bw && bw.getMessage().getString().equals("Schließen")) {
                    closeButton = bw;
                    break;
                }
            }
            if(closeButton != null) {
                feedbackY = closeButton.getY() - this.textRenderer.fontHeight - 5;
            }
            feedbackY = Math.max(5, Math.min(feedbackY, this.height - this.textRenderer.fontHeight - 5));
            context.drawCenteredTextWithShadow(this.textRenderer, feedbackText, this.width / 2, feedbackY, 0xFFFFFF);
        } else {
            feedbackText = null;
        }
    }


    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(null);
        } else {
            super.close();
        }
    }

    private static class FlyingText {
        String text;
        float x, y, dx, dy;
        int color;
        int width;
        int textHeight;
        float colorTime;

        FlyingText(String text, float x, float y, float dx, float dy, int initialColor, int width) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.dy = dy;
            this.color = initialColor;
            this.width = width;
            this.colorTime = (float) (Math.random() * 100);
        }

        void update(int screenWidth, int screenHeight, int fontHeight) {
            this.textHeight = fontHeight;
            x += dx;
            y += dy;
            colorTime += 0.05f;

            if (x < 0) {
                x = 0;
                dx *= -1;
            } else if (x + width > screenWidth) {
                x = screenWidth - width;
                dx *= -1;
            }

            if (y < 0) {
                y = 0;
                dy *= -1;
            } else if (y + textHeight > screenHeight) {
                y = screenHeight - textHeight;
                dy *= -1;
            }
        }

        boolean isOutOfBounds(int screenWidth, int screenHeight) {
            int buffer = 50;
            return x + width < -buffer || x > screenWidth + buffer ||
                    y + textHeight < -buffer || y > screenHeight + buffer;
        }
    }
}