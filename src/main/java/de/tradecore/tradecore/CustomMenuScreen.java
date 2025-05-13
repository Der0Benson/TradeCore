package de.tradecore.tradecore; // Dein package

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier; // Für Texturen, falls du später welche brauchst

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CustomMenuScreen extends Screen {

    private final String[] flyingNames = {"Der_Wolfo_", "Der_Benson_", "Juleszlive"};
    private List<FlyingText> activeFlyingTexts;
    private Random random = new Random();

    // Optional: Für Hintergrund
    // private static final Identifier BACKGROUND_TEXTURE = new Identifier(TradeCore.MOD_ID, "textures/gui/custom_menu_background.png");

    public CustomMenuScreen() {
        super(Text.literal("Hauptmenü").formatted(Formatting.BOLD)); // Titel des Screens
        activeFlyingTexts = new ArrayList<>();
    }

    @Override
    protected void init() {
        super.init();
        if (this.client == null) {
            return;
        }

        int screenWidth = this.width;
        int screenHeight = this.height;
        int centerX = screenWidth / 2;
        int buttonWidth = 200;
        int buttonHeight = 20;
        int spacing = 5; // Abstand zwischen Buttons

        // StartY für die Buttons, z.B. zentriert mit etwas Offset
        int startY = screenHeight / 2 - (3 * buttonHeight + 2 * spacing) / 2;

        // Button: Impressum
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Impressum"), button -> {
                    // Aktion für Impressum, z.B. Link öffnen
                    try {
                        // Ersetze dies mit deinem tatsächlichen Impressums-Link
                        java.awt.Desktop.getDesktop().browse(new URI("https://deine-impressum-url.de"));
                    } catch (Exception e) {
                        TradeCore.LOGGER.error("Konnte Impressum-URL nicht öffnen", e);
                        if (client.player != null) {
                            client.player.sendMessage(Text.literal("Konnte Link nicht öffnen.").formatted(Formatting.RED), false);
                        }
                    }
                })
                .dimensions(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight)
                .build());

        // Button: Wiki
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Wiki"), button -> {
                    // Aktion für Wiki
                    try {
                        // Ersetze dies mit deinem tatsächlichen Wiki-Link
                        java.awt.Desktop.getDesktop().browse(new URI("https://deine-wiki-url.de"));
                    } catch (Exception e) {
                        TradeCore.LOGGER.error("Konnte Wiki-URL nicht öffnen", e);
                        if (client.player != null) {
                            client.player.sendMessage(Text.literal("Konnte Link nicht öffnen.").formatted(Formatting.RED), false);
                        }
                    }
                })
                .dimensions(centerX - buttonWidth / 2, startY + buttonHeight + spacing, buttonWidth, buttonHeight)
                .build());

        // Button: Bewerben
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Bewerben"), button -> {
                    // Aktion für Bewerben
                    try {
                        // Ersetze dies mit deinem tatsächlichen Bewerbungs-Link
                        java.awt.Desktop.getDesktop().browse(new URI("https://deine-bewerben-url.de"));
                    } catch (Exception e) {
                        TradeCore.LOGGER.error("Konnte Bewerben-URL nicht öffnen", e);
                        if (client.player != null) {
                            client.player.sendMessage(Text.literal("Konnte Link nicht öffnen.").formatted(Formatting.RED), false);
                        }
                    }
                })
                .dimensions(centerX - buttonWidth / 2, startY + 2 * (buttonHeight + spacing), buttonWidth, buttonHeight)
                .build());

        // Schließen Button (optional, da ESC auch funktioniert)
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Schließen"), button -> this.close())
                .dimensions(centerX - buttonWidth / 2, startY + 3 * (buttonHeight + spacing) + 20, buttonWidth, buttonHeight)
                .build());

        // Initialisiere einige fliegende Namen
        for (String name : flyingNames) {
            spawnFlyingText(name);
        }
    }

    private void spawnFlyingText(String text) {
        if (this.client == null) return;
        float x = random.nextFloat() * this.width;
        float y = random.nextFloat() * this.height;
        // Richtung zufällig, aber nicht zu langsam/schnell
        float dx = (random.nextFloat() - 0.5f) * 2; // Zwischen -1 und 1
        float dy = (random.nextFloat() - 0.5f) * 2; // Zwischen -1 und 1
        int color = 0xFFFFFF; // Weiß, du kannst das auch zufällig machen
        activeFlyingTexts.add(new FlyingText(text, x, y, dx, dy, color, this.textRenderer.getWidth(text)));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.client == null) return;

        // Füge gelegentlich neue Namen hinzu oder wenn zu wenige da sind
        if (activeFlyingTexts.size() < flyingNames.length && random.nextInt(100) < 5) { // 5% Chance pro Tick, wenn nicht voll
            spawnFlyingText(flyingNames[random.nextInt(flyingNames.length)]);
        }

        List<FlyingText> toRemove = new ArrayList<>();
        for (FlyingText ft : activeFlyingTexts) {
            ft.update();
            // Entferne Text, wenn er außerhalb des Bildschirms ist
            if (ft.x + ft.width < 0 || ft.x > this.width || ft.y + this.client.textRenderer.fontHeight < 0 || ft.y > this.height) {
                toRemove.add(ft);
            }
        }
        activeFlyingTexts.removeAll(toRemove);

        // Füge entfernte wieder hinzu, um die Anzahl konstant zu halten (optional)
        if (toRemove.size() > 0 && activeFlyingTexts.size() < flyingNames.length * 2) { // *2 um etwas Variation zu haben
            for (int i = 0; i < toRemove.size(); i++) {
                spawnFlyingText(flyingNames[random.nextInt(flyingNames.length)]);
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Hintergrund rendern (optional mit Textur)
        this.renderBackground(context, mouseX, mouseY, delta);
        // oder: context.drawTexture(BACKGROUND_TEXTURE, 0, 0, 0, 0, this.width, this.height, this.width, this.height);


        // Titel zentriert oben
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        // Fliegende Namen rendern
        for (FlyingText ft : activeFlyingTexts) {
            context.drawTextWithShadow(this.textRenderer, ft.text, (int) ft.x, (int) ft.y, ft.color);
        }

        // Widgets (Buttons) rendern
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false; // Das Spiel im Hintergrund nicht pausieren
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(null);
        } else {
            super.close();
        }
    }

    // Innere Klasse für die fliegenden Texte
    private static class FlyingText {
        String text;
        float x, y, dx, dy;
        int color;
        int width; // Breite des Textes für Boundary Checks

        FlyingText(String text, float x, float y, float dx, float dy, int color, int width) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.dy = dy;
            this.color = color;
            this.width = width;
        }

        void update() {
            x += dx;
            y += dy;

            // Primitive Kollision mit den Rändern (Abprallen)
            // Hier brauchst du die Screen-Breite und -Höhe, die du von der äußeren Klasse bekommst
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen != null) {
                Screen currentScreen = client.currentScreen;
                if (x < 0 || x + width > currentScreen.width) {
                    dx *= -1; // Richtung umkehren
                    x = Math.max(0, Math.min(x, currentScreen.width - width)); // Stelle sicher, dass es im Screen bleibt
                }
                if (y < 0 || y + client.textRenderer.fontHeight > currentScreen.height) {
                    dy *= -1; // Richtung umkehren
                    y = Math.max(0, Math.min(y, currentScreen.height - client.textRenderer.fontHeight)); // Stelle sicher
                }
            }
        }
    }
}