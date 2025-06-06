// Gesamte Klasse TutorialScreen.java (nur die render-Methode ist hier relevant geändert)
// ... (Anfang der Klasse, Imports, RAW_TUTORIAL_TEXT, TutorialLineElement, Konstruktor, prepareTutorialText, openLink, init unverändert) ...

package de.tradecore.tradecore;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class TutorialScreen extends Screen {

    private List<TutorialLineElement> tutorialElements = new ArrayList<>();
    private static final int PADDING = 15;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_MARGIN_BOTTOM = 10;
    private static final int INTER_ELEMENT_SPACING = 4;
    private static final int HEADLINE_BOTTOM_MARGIN = 6;

    private static final String RAW_TUTORIAL_TEXT =
            "Willkommen bei TradeCore – der Modifikation für ein smartes, transparentes und spielerfreundliches Wirtschaftssystem auf dem GrieferGames-Netzwerk. Ob du ein neuer Spieler bist oder schon lange dabei: Mit TradeCore holst du das Beste aus deinem Handel raus! , " +
                    "**Hauptmenü öffnen (Pfeiltaste nach oben)** , " +
                    "Hier findest du das zentrale Verwaltungsmenü der Mod. Drücke die Pfeiltaste nach oben, um folgende Funktionen zu erreichen: Preise aktualisierenAktualisiere die Itempreise in deiner Mod alle 60 Minuten. , " +
                    "So bleibst du immer auf dem aktuellen Stand der Community-Preise. , " +
                    "**Preisanzeige konfigurieren**Du kannst festlegen, ob die Preisanzeige: , " +
                    "Permanent eingeblendet bleibt, oder , " +
                    "Nur beim Drücken der SHIFT-Taste sichtbar ist (Standardeinstellung, nicht TAB). , " +
                    "Stelle das nach deinem Geschmack ein! , " +
                    "**Mod deaktivieren** , " +
                    "Du spielst auch mal auf anderen Netzwerken? Kein Problem! Hier kannst du TradeCore vorübergehend deaktivieren, damit die Mod nicht stört. Vergiss nur nicht, sie später wieder einzuschalten, wenn du zurück auf GrieferGames bist! Sonstige Funktionen , " +
                    "Direktzugriff auf: , " +
                    "Impressum , " +
                    "Wiki , " +
                    "Bewerbungssystem für das TradeCore-Team , " +
                    "**Itempreise festlegen (Pfeiltaste nach unten)** , " +
                    "Du willst helfen, faire Preise zu definieren? So geht’s: So funktioniert’s:Halte das Item, das du bewerten willst, in der Haupthand. , " +
                    "Drücke die Pfeiltaste nach unten. , " +
                    "Ein Eingabemenü erscheint – hier kannst du den: , " +
                    "Einzelpreis , " +
                    "Stackpreis (64 Stück) , " +
                    "Doppelkistenpreis (54 Stacks)eingeben. , " +
                    "**Preisanzeige im Spiel:**Sobald ein Preis existiert, zeigt TradeCore ihn direkt unter dem Itemnamen an (hellgrün). , " +
                    "Du siehst auf einen Blick den Durchschnittspreis, egal ob einzelnes Item oder ganze Doppelkiste. , " +
                    "Die Daten basieren auf Einsendungen echter Spieler – TradeCore berechnet daraus einen Mittelwert, sobald mindestens drei Spieler Preise angegeben haben. , " +
                    "Wurde noch kein Preis eingetragen? Dann wird dein Eintrag als Startwert genommen! , " +
                    "**Preisübersicht im Web:** , " +
                    "Alle Itempreise findest du auch online: , " +
                    "TradeCore Preisübersicht (Nur zum Nachschauen – nicht bearbeitbar!) , " +
                    "**Block des Tages verwalten (Pfeiltaste nach rechts)** , " +
                    "Der Block des Tages bringt nicht nur Spannung ins Spiel, sondern auch Belohnungen!  , " +
                    "**Was ist der Block des Tages?**Jeden Tag um 4 Uhr morgens wird ein neuer Block als „Block des Tages“ bestimmt. , " +
                    "Wer diesen Block abbaut, hat eine zufällige Chance auf: , " +
                    "Ingame-Geld , " +
                    "Kristalle , " +
                    "Seltene Köpfe oder Items , " +
                    "**So machst du mit:**Öffne das Menü mit der rechten Pfeiltaste. , " +
                    "Hier kannst du: , " +
                    "Den aktuellen Block des Tages ansehen , " +
                    "Einen neuen Block vorschlagen , " +
                    "Angeben, ob der Block sich heute „schnell“ oder „langsam“ abbauen lässt , " +
                    "**Alternative Eintragsmethoden:**Auf der Webseite: Block des Tages , " +
                    "Oder im Discord-Kanal #block-des-tages: Discord beitreten , " +
                    "**Twitch-Integration für Partner**Streamer mit TradeCore-Partnerschaft können im Stream-Chat den Befehl !bdt nutzen. , " +
                    "Der Chat bekommt dann automatisch die aktuelle Info aus dem System, z.B.: , " +
                    "[TradeCore] Block des Tages: Diamanterz | Gewinn: 2500$ | Votes: 8 Schnell / 3 Langsam , " +
                    "**Noch Fragen?** , " +
                    "Dann schau im Wiki vorbei oder komm direkt auf unseren Discord-Server – dort helfen dir Spieler und Teammitglieder gerne weiter!";


    private static class TutorialLineElement {
        Text text;
        boolean isHeadline;

        TutorialLineElement(Text text, boolean isHeadline) {
            this.text = text;
            this.isHeadline = isHeadline;
        }
    }

    public TutorialScreen() {
        super(Text.literal("TradeCore Tutorial – Dein Einstieg").formatted(Formatting.GOLD, Formatting.BOLD));
        prepareTutorialText();
    }

    private void prepareTutorialText() {
        String[] paragraphs = RAW_TUTORIAL_TEXT.split("\\s*,\\s*\\n*\\s*");
        tutorialElements.clear();

        for (String paragraph : paragraphs) {
            String currentSegment = paragraph.trim();
            if (currentSegment.isEmpty()) {
                if (!tutorialElements.isEmpty() && !isLastElementEmptySpace()) {
                    tutorialElements.add(new TutorialLineElement(Text.literal(" "), false));
                }
                continue;
            }

            if (currentSegment.startsWith("**")) {
                int closingStarsIndex = currentSegment.indexOf("**", 2);
                if (closingStarsIndex != -1) {
                    String title = currentSegment.substring(2, closingStarsIndex);
                    if (!tutorialElements.isEmpty() && !isLastElementEmptySpace()) {
                        tutorialElements.add(new TutorialLineElement(Text.literal(" "), false));
                    }
                    tutorialElements.add(new TutorialLineElement(Text.literal(title).formatted(Formatting.YELLOW, Formatting.BOLD), true));

                    String remainingText = currentSegment.substring(closingStarsIndex + 2).trim();
                    if (!remainingText.isEmpty()) {
                        tutorialElements.add(new TutorialLineElement(Text.literal(remainingText.replace("\\n", "\n")).formatted(Formatting.WHITE), false));
                    }
                } else {
                    tutorialElements.add(new TutorialLineElement(Text.literal(currentSegment.replace("\\n", "\n")).formatted(Formatting.WHITE), false));
                }
            } else {
                tutorialElements.add(new TutorialLineElement(Text.literal(currentSegment.replace("\\n", "\n")).formatted(Formatting.WHITE), false));
            }
        }
    }

    private boolean isLastElementEmptySpace() {
        if (tutorialElements.isEmpty()) {
            return false;
        }
        return tutorialElements.get(tutorialElements.size() - 1).text.getString().equals(" ");
    }


    private void openLink(String url) {
        try {
            Util.getOperatingSystem().open(new URI(url));
        } catch (Exception e) {
            TradeCore.LOGGER.error("Konnte Link nicht öffnen: {}", url, e); //
        }
    }

    @Override
    protected void init() {
        super.init();
        if (this.client == null) return;
        this.scrollOffset = 0;

        int buttonY = this.height - BUTTON_HEIGHT - BUTTON_MARGIN_BOTTOM;
        int buttonWidth = 100;
        int centerX = this.width / 2;
        int spacing = 4;

        int combinedWidth = buttonWidth * 2 + spacing;
        int startX = centerX - combinedWidth / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Discord"), button -> {
                    openLink("https://discord.gg/vZvdnJwZrn");
                })
                .dimensions(startX, buttonY, buttonWidth, BUTTON_HEIGHT)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Verstanden").formatted(Formatting.GREEN), button -> {
                    TradeCoreConfig.markTutorialAsShown(); //
                    if (this.client != null) {
                        this.client.setScreen(null);
                    }
                })
                .dimensions(startX + buttonWidth + spacing, buttonY, buttonWidth, BUTTON_HEIGHT)
                .build());
    }

    private int scrollOffset = 0;
    private final int fontHeight = 9;

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        // Titel des Screens
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, PADDING, 0xFFFFFF);

        int currentY = PADDING + this.textRenderer.fontHeight + INTER_ELEMENT_SPACING + 5; // Start-Y-Position für den Textinhalt
        int textRenderWidth = this.width - 2 * PADDING; // Maximale Breite für umgebrochenen Text
        TextRenderer tr = this.textRenderer;

        for (TutorialLineElement element : tutorialElements) {
            int elementHeight;
            int startDrawingY = currentY - scrollOffset; // Y-Position des aktuellen Elements unter Berücksichtigung des Scrollens

            // Sichtbarkeitsgrenzen für den Text
            int bottomScreenEdgeForText = this.height - PADDING - BUTTON_HEIGHT - BUTTON_MARGIN_BOTTOM;
            int topScreenEdgeForText = PADDING + tr.fontHeight + INTER_ELEMENT_SPACING;

            // Behandlung für explizite Leerzeilen-Elemente (für Abstände)
            if (element.text.getString().equals(" ") && !element.isHeadline) {
                elementHeight = fontHeight / 2; // Kleinere Höhe für Leerzeilen
                // Optional: Sichtbarkeitsprüfung für Leerzeilen, meist aber nicht nötig
                currentY += elementHeight; // Kein INTER_ELEMENT_SPACING nach einer reinen Leerzeile
                continue; // Nichts zeichnen, nur Y-Position anpassen
            }

            if (element.isHeadline) {
                elementHeight = tr.fontHeight; // Überschriften sind einzeilig
                // Nur zeichnen, wenn die Überschrift (oder ein Teil davon) im sichtbaren Bereich ist
                if (startDrawingY + elementHeight > topScreenEdgeForText && startDrawingY < bottomScreenEdgeForText) {
                    context.drawCenteredTextWithShadow(tr, element.text, this.width / 2, startDrawingY, Formatting.YELLOW.getColorValue());
                }
                currentY += elementHeight + HEADLINE_BOTTOM_MARGIN; // Y-Position für das nächste Element anpassen
            } else {
                // Für normale Absätze, die umgebrochen werden
                List<OrderedText> wrappedOrderedLines = tr.wrapLines(element.text, textRenderWidth);
                elementHeight = wrappedOrderedLines.size() * tr.fontHeight; // Gesamthöhe des umgebrochenen Absatzes

                int lineY = startDrawingY; // Start-Y-Position für die erste Zeile des aktuellen Absatzes
                for (OrderedText wrappedLine : wrappedOrderedLines) {
                    // Nur zeichnen, wenn die aktuelle Zeile (oder ein Teil davon) im sichtbaren Bereich ist
                    if (lineY + tr.fontHeight > topScreenEdgeForText && lineY < bottomScreenEdgeForText) {
                        // *** NEU: Textzeile zentrieren ***
                        int lineWidth = tr.getWidth(wrappedLine);
                        int xCentered = (this.width - lineWidth) / 2;
                        context.drawTextWithShadow(tr, wrappedLine, xCentered, lineY, Formatting.WHITE.getColorValue());
                    }
                    lineY += tr.fontHeight; // Y-Position für die nächste Zeile des Absatzes
                }
                currentY += elementHeight + INTER_ELEMENT_SPACING; // Y-Position für das nächste Element anpassen
            }
        }
        super.render(context, mouseX, mouseY, delta); // Zeichnet die Buttons
    }

    // calculateTotalTextHeight, mouseScrolled, shouldPause, close Methoden bleiben unverändert
    // ... (Rest der Klasse wie in der vorherigen Antwort) ...
    private int calculateTotalTextHeight() {
        int totalHeight = 0;
        int textRenderWidth = this.width - 2 * PADDING;
        TextRenderer tr = this.textRenderer;

        for (TutorialLineElement element : tutorialElements) {
            if (element.text.getString().equals(" ") && !element.isHeadline) {
                totalHeight += fontHeight / 2;
                continue;
            }
            if (element.isHeadline) {
                totalHeight += tr.fontHeight;
                totalHeight += HEADLINE_BOTTOM_MARGIN;
            } else {
                totalHeight += tr.wrapLines(element.text, textRenderWidth).size() * tr.fontHeight;
                totalHeight += INTER_ELEMENT_SPACING;
            }
        }
        return totalHeight;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int scrollAmount = (int) (verticalAmount * fontHeight * 1.5);
        int newScrollOffset = this.scrollOffset - scrollAmount;

        int totalTextHeight = calculateTotalTextHeight();
        int topMarginForTextDisplay = PADDING + this.textRenderer.fontHeight + INTER_ELEMENT_SPACING + 5;
        int bottomMarginForTextDisplay = this.height - PADDING - BUTTON_HEIGHT - BUTTON_MARGIN_BOTTOM;
        int visibleTextDisplayAreaHeight = bottomMarginForTextDisplay - topMarginForTextDisplay;

        int maxScroll = Math.max(0, totalTextHeight - visibleTextDisplayAreaHeight + PADDING);
        if (totalTextHeight <= visibleTextDisplayAreaHeight) {
            maxScroll = 0;
        }

        this.scrollOffset = Math.max(0, Math.min(newScrollOffset, maxScroll));
        return true;
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(null);
        } else {
            super.close();
        }
    }
}