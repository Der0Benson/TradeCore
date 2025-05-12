package de.tradecore.tradecore;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.concurrent.CompletableFuture;

public class BlockOfTheDayScreen extends Screen {

    // Originale Textanzeigen
    private Text itemText = Text.literal("Laden...").formatted(Formatting.YELLOW);
    private Text gewinnText = Text.literal("Laden...").formatted(Formatting.YELLOW);
    private Text statusText = Text.literal(""); // Für Feedback (Laden, Fehler, Erfolg etc.)

    // Anzeigen für Abstimmung
    private Text voteStatusText = Text.literal("").formatted(Formatting.GRAY);
    private Text voteResultText = Text.literal("").formatted(Formatting.GRAY);

    // Widgets
    private TextWidget itemDisplayWidget;
    private TextWidget gewinnDisplayWidget;
    private TextWidget voteStatusDisplayWidget;
    private TextWidget voteResultDisplayWidget;
    private ButtonWidget createBdtButton;
    private ButtonWidget closeButton;
    private ButtonWidget voteSchnellButton;
    private ButtonWidget voteLangsamButton;
    private ButtonWidget refreshButton; // Aktualisieren-Button
    private ButtonWidget editBdtButton; // NEU

    // Zustand
    private String currentBdtId = null;
    private boolean canVote = false;
    private boolean isLoadingData = false;
    private boolean isSubmittingVote = false;
    private long remainingEditCooldown = 0; // NEU: Feld für verbleibenden Cooldown

    protected BlockOfTheDayScreen() {
        super(Text.literal("Block des Tages"));
    }

    @Override
    protected void init() {
        super.init();
        if (this.client == null || TradeCore.apiClient == null) {
            return;
        }

        int screenWidth = this.width;
        int screenHeight = this.height;
        int centerX = screenWidth / 2;
        int contentStartY = screenHeight / 2 - 45;
        int widgetWidth = 200;
        int widgetHeight = 20;
        int spacing = 5;
        int bottomButtonY = screenHeight - 30;

        // Titel
        this.addDrawableChild(new TextWidget(0, 20, screenWidth, this.textRenderer.fontHeight, this.title, this.textRenderer).alignCenter());

        // Item-Anzeige
        itemDisplayWidget = new TextWidget(centerX - widgetWidth / 2, contentStartY, widgetWidth, widgetHeight, itemText, this.textRenderer);
        itemDisplayWidget.alignLeft();
        this.addDrawableChild(itemDisplayWidget);

        // Gewinn-Anzeige
        gewinnDisplayWidget = new TextWidget(centerX - widgetWidth / 2, contentStartY + widgetHeight + spacing, widgetWidth, widgetHeight, gewinnText, this.textRenderer);
        gewinnDisplayWidget.alignLeft();
        this.addDrawableChild(gewinnDisplayWidget);

        // Anzeige der Abstimmungsergebnisse
        voteResultDisplayWidget = new TextWidget(centerX - widgetWidth / 2, contentStartY + 2 * (widgetHeight + spacing), widgetWidth, widgetHeight, voteResultText, this.textRenderer);
        voteResultDisplayWidget.alignLeft();
        this.addDrawableChild(voteResultDisplayWidget);

        // Abstimmungsbuttons
        int voteButtonY = contentStartY + 3 * (widgetHeight + spacing) + 10;
        voteSchnellButton = ButtonWidget.builder(Text.literal("Schnell").formatted(Formatting.GREEN), button -> submitVote("schnell"))
                .dimensions(centerX - widgetWidth / 2, voteButtonY, widgetWidth / 2 - spacing, widgetHeight)
                .build();
        voteSchnellButton.active = false;
        this.addDrawableChild(voteSchnellButton);

        voteLangsamButton = ButtonWidget.builder(Text.literal("Langsam").formatted(Formatting.RED), button -> submitVote("langsam"))
                .dimensions(centerX + spacing, voteButtonY, widgetWidth / 2 - spacing, widgetHeight)
                .build();
        voteLangsamButton.active = false;
        this.addDrawableChild(voteLangsamButton);

        // Anzeige des Abstimmungsstatus
        voteStatusDisplayWidget = new TextWidget(centerX - widgetWidth / 2, voteButtonY + widgetHeight + spacing, widgetWidth, widgetHeight, voteStatusText, this.textRenderer);
        voteStatusDisplayWidget.alignCenter();
        this.addDrawableChild(voteStatusDisplayWidget);

        // Button zum Erstellen
        int createButtonY = voteButtonY + widgetHeight + spacing + widgetHeight + spacing + 5;
        createBdtButton = ButtonWidget.builder(Text.literal("Neuen BdT erstellen...").formatted(Formatting.GREEN), button -> {
                    if (this.client != null) {
                        this.client.setScreen(new CreateBdtScreen(this));
                    }
                })
                .dimensions(centerX - 100, createButtonY, 200, 20)
                .build();
        createBdtButton.visible = false;
        this.addDrawableChild(createBdtButton);

        // NEU: Button zum Bearbeiten
        editBdtButton = ButtonWidget.builder(Text.literal("BdT bearbeiten").formatted(Formatting.YELLOW), button -> {
                    if (this.client != null) {
                        String itemName = itemText.getString().replace("Item: ", "").trim();
                        String gewinn = gewinnText.getString().replace("Gewinn: ", "").trim();
                        this.client.setScreen(new EditBdtScreen(this, currentBdtId, itemName, gewinn));
                    }
                })
                .dimensions(centerX - 100, createButtonY + 25, 200, 20)
                .build();
        editBdtButton.visible = false; // Zunächst unsichtbar
        this.addDrawableChild(editBdtButton);

        // --- Untere Button-Reihe ---
        int bottomButtonWidth = 98;

        // Aktualisieren Button - Ruft jetzt fetchDataAndUpdateScreen(true) auf
        refreshButton = ButtonWidget.builder(Text.literal("Aktualisieren"), button -> {
                    TradeCore.LOGGER.info("Refresh Button geklickt. isLoadingData = {}", isLoadingData);
                    if (!isLoadingData) {
                        TradeCore.LOGGER.info(" -> Starte fetchDataAndUpdateScreen(true)");
                        fetchDataAndUpdateScreen(true); // true für Force Refresh
                    } else {
                        TradeCore.LOGGER.info(" -> Fetch nicht gestartet (isLoadingData ist true).");
                    }
                })
                .dimensions(centerX - bottomButtonWidth - spacing / 2, bottomButtonY, bottomButtonWidth, 20)
                .build();
        this.addDrawableChild(refreshButton);

        // Schließen Button
        closeButton = ButtonWidget.builder(Text.literal("Schließen"), button -> this.close())
                .dimensions(centerX + spacing / 2, bottomButtonY, bottomButtonWidth, 20)
                .build();
        this.addDrawableChild(closeButton);

        // Daten initial abrufen (ohne Force)
        fetchDataAndUpdateScreen(false); // false für initialen Load (Cache OK)
    }

    // *** fetchDataAndUpdateScreen mit forceRefresh Parameter ***
    private void fetchDataAndUpdateScreen(boolean forceRefresh) {
        TradeCore.LOGGER.info("fetchDataAndUpdateScreen aufgerufen. forceRefresh = {}, Aktueller isLoadingData = {}", forceRefresh, isLoadingData);

        if (isLoadingData) {
            TradeCore.LOGGER.warn(" -> Abbruch: isLoadingData ist bereits true.");
            return;
        }
        isLoadingData = true;
        setFeedback(Text.literal("Lade Daten...").formatted(Formatting.YELLOW), 5000);
        if (refreshButton != null) refreshButton.active = false;
        resetUIState();

        // Entscheide, welche API-Methode aufgerufen wird
        CompletableFuture<PriceAPIClient.BlockOfTheDayResult> future;
        if (forceRefresh) {
            future = TradeCore.apiClient.forceFetchBlockOfTheDayAsync();
        } else {
            future = TradeCore.apiClient.fetchBlockOfTheDayAsync();
        }

        future.whenCompleteAsync((result, throwable) -> {
            isLoadingData = false;
            if (refreshButton != null) refreshButton.active = true;
            TradeCore.LOGGER.info("BdT Fetch whenComplete: isLoadingData auf false gesetzt, refreshButton aktiviert.");

            if (throwable != null) {
                TradeCore.LOGGER.error("Fehler beim Abrufen des BdT im whenComplete:", throwable);
                setFeedback(Text.literal("Fehler beim Laden! (Logs prüfen)").formatted(Formatting.RED), 5000);
                this.currentBdtId = null;
                this.itemText = Text.literal("Fehler").formatted(Formatting.RED);
                this.gewinnText = Text.literal("Fehler").formatted(Formatting.RED);
                this.voteResultText = Text.literal("").formatted(Formatting.GRAY);
                this.voteStatusText = Text.literal("").formatted(Formatting.GRAY);
                this.canVote = false;
                this.remainingEditCooldown = 0;
                if (voteSchnellButton != null) voteSchnellButton.active = false;
                if (voteLangsamButton != null) voteLangsamButton.active = false;
                if (createBdtButton != null) createBdtButton.visible = false;
                if (editBdtButton != null) editBdtButton.visible = false;

            } else if (result != null) {
                if (result.found) {
                    this.currentBdtId = result.bdtId;
                    this.itemText = Text.literal("Item: ").append(Text.literal(result.itemName).formatted(Formatting.AQUA));
                    this.gewinnText = Text.literal("Gewinn: ").append(Text.literal(result.gewinn).formatted(Formatting.GOLD));
                    updateVoteDisplay(result.schnellVotes, result.langsamVotes);
                    this.canVote = !result.hasVoted;
                    this.remainingEditCooldown = result.remainingEditCooldown;
                    updateVoteStatus(result.hasVoted);
                    setFeedback(null, 0);
                    if (createBdtButton != null) createBdtButton.visible = false;
                    if (editBdtButton != null) {
                        editBdtButton.visible = true; // Show edit button if BdT exists
                        updateEditButtonState();
                    }
                } else {
                    this.currentBdtId = null;
                    this.itemText = Text.literal("-").formatted(Formatting.GRAY);
                    this.gewinnText = Text.literal("-").formatted(Formatting.GRAY);
                    this.voteResultText = Text.literal("").formatted(Formatting.GRAY);
                    this.voteStatusText = Text.literal("").formatted(Formatting.GRAY);
                    this.canVote = false;
                    this.remainingEditCooldown = 0;
                    if (voteSchnellButton != null) voteSchnellButton.active = false;
                    if (voteLangsamButton != null) voteLangsamButton.active = false;
                    setFeedback(Text.literal(result.message != null ? result.message : "Fehler").formatted(Formatting.RED), 0);
                    if (createBdtButton != null) {
                        createBdtButton.visible = true;
                    } else {
                        TradeCore.LOGGER.error("createBdtButton war null in Callback!");
                    }
                    if (editBdtButton != null) editBdtButton.visible = false;
                }
            } else {
                TradeCore.LOGGER.error("BdT Ergebnis war null ohne Fehler!");
                setFeedback(Text.literal("Interner Fehler (Result null)").formatted(Formatting.RED), 5000);
                this.remainingEditCooldown = 0;
            }

            if (itemDisplayWidget != null) itemDisplayWidget.setMessage(itemText);
            if (gewinnDisplayWidget != null) gewinnDisplayWidget.setMessage(gewinnText);
            if (voteResultDisplayWidget != null) voteResultDisplayWidget.setMessage(voteResultText);
            if (voteStatusDisplayWidget != null) voteStatusDisplayWidget.setMessage(voteStatusText);

        }, MinecraftClient.getInstance());
    }

    private void resetUIState() {
        this.itemText = Text.literal("Laden...").formatted(Formatting.YELLOW);
        this.gewinnText = Text.literal("Laden...").formatted(Formatting.YELLOW);
        this.voteResultText = Text.literal("Laden...").formatted(Formatting.YELLOW);
        this.voteStatusText = Text.literal("");
        this.currentBdtId = null;
        this.canVote = false;
        this.remainingEditCooldown = 0;

        if (itemDisplayWidget != null) itemDisplayWidget.setMessage(itemText);
        if (gewinnDisplayWidget != null) gewinnDisplayWidget.setMessage(gewinnText);
        if (voteResultDisplayWidget != null) voteResultDisplayWidget.setMessage(voteResultText);
        if (voteStatusDisplayWidget != null) voteStatusDisplayWidget.setMessage(voteStatusText);
        if (voteSchnellButton != null) voteSchnellButton.active = false;
        if (voteLangsamButton != null) voteLangsamButton.active = false;
        if (createBdtButton != null) createBdtButton.visible = false;
        if (editBdtButton != null) editBdtButton.visible = false;
        TradeCore.LOGGER.info("resetUIState ausgeführt.");
    }

    private void updateVoteDisplay(int schnellVotes, int langsamVotes) {
        int totalVotes = schnellVotes + langsamVotes;
        MutableText result = Text.literal("Aufwand: ").formatted(Formatting.WHITE);
        if (totalVotes == 0) {
            result.append(Text.literal("Noch keine Stimmen").formatted(Formatting.GRAY));
        } else {
            result.append(Text.literal("Schnell: ").formatted(Formatting.GREEN)).append(Text.literal(String.valueOf(schnellVotes))).append(Text.literal(" / ").formatted(Formatting.GRAY)).append(Text.literal("Langsam: ").formatted(Formatting.RED)).append(Text.literal(String.valueOf(langsamVotes)));
        }
        this.voteResultText = result;
        if (voteResultDisplayWidget != null) voteResultDisplayWidget.setMessage(this.voteResultText);
    }

    private void updateVoteStatus(boolean hasVoted) {
        boolean shouldBeActive = !isLoadingData && !isSubmittingVote && this.currentBdtId != null && !hasVoted;

        if (hasVoted) {
            this.canVote = false;
            this.voteStatusText = Text.literal("Du hast bereits abgestimmt.").formatted(Formatting.YELLOW);
        } else if (this.currentBdtId != null) {
            this.canVote = true;
            this.voteStatusText = Text.literal("Wie schnell ist dieser BdT?").formatted(Formatting.AQUA);
        } else {
            this.canVote = false;
            this.voteStatusText = Text.literal("");
        }

        if (voteSchnellButton != null) voteSchnellButton.active = shouldBeActive;
        if (voteLangsamButton != null) voteLangsamButton.active = shouldBeActive;
        if (voteStatusDisplayWidget != null) voteStatusDisplayWidget.setMessage(this.voteStatusText);
    }

    private void submitVote(String voteType) {
        TradeCore.LOGGER.info("submitVote aufgerufen für Typ: {}. CanVote={}, isSubmitting={}, currentBdtId={}", voteType, canVote, isSubmittingVote, currentBdtId);
        if (!canVote || isSubmittingVote || currentBdtId == null || this.client == null || this.client.player == null) {
            TradeCore.LOGGER.warn(" -> Abbruch: Bedingungen nicht erfüllt.");
            return;
        }
        isSubmittingVote = true;
        updateVoteStatus(false); // Deaktiviere Vote-Buttons
        setFeedback(Text.literal("Sende Abstimmung...").formatted(Formatting.YELLOW), 3000);

        String playerUuid = this.client.player.getUuidAsString();

        TradeCore.apiClient.submitBdtVoteAsync(currentBdtId, playerUuid, voteType)
                .whenCompleteAsync((success, throwable) -> {
                    isSubmittingVote = false;
                    TradeCore.LOGGER.info("submitBdtVoteAsync whenComplete: isSubmittingVote auf false gesetzt.");

                    if (throwable != null) {
                        TradeCore.LOGGER.error("Fehler beim Senden der Abstimmung:", throwable);
                        setFeedback(Text.literal("Fehler beim Senden!").formatted(Formatting.RED), 5000);
                        updateVoteStatus(this.canVote); // Setze basierend auf Zustand VOR dem Senden zurück

                    } else if (success != null && success) {
                        setFeedback(Text.literal("Stimme erfolgreich abgegeben! (Aktualisiere zum Sehen)").formatted(Formatting.GREEN), 4000); // Hinweis hinzugefügt
                        this.canVote = false; // Spieler hat jetzt abgestimmt
                        updateVoteStatus(true); // Zeige "Du hast bereits.." und deaktiviere Buttons
                        // KEIN automatischer Refresh hier
                    } else {
                        setFeedback(Text.literal("Fehler: Stimme nicht akzeptiert (Bereits abgestimmt?).").formatted(Formatting.RED), 5000);
                        this.canVote = false; // Gehe davon aus, dass der Server "bereits abgestimmt" meint
                        updateVoteStatus(true);
                    }
                }, MinecraftClient.getInstance());
    }

    private void setFeedback(Text message, long durationMillis) {
        // TODO: Evtl. Timeout-Logik für Feedback implementieren
        this.statusText = message != null ? message : Text.literal("");
    }

    private void updateEditButtonState() {
        if (editBdtButton == null) return;
        if (this.currentBdtId != null && this.remainingEditCooldown <= 0) {
            editBdtButton.active = true;
            editBdtButton.setMessage(Text.literal("BdT bearbeiten").formatted(Formatting.YELLOW));
        } else {
            editBdtButton.active = false;
            long remainingSeconds = this.remainingEditCooldown / 1000; // Convert milliseconds to seconds
            long hours = remainingSeconds / 3600;
            remainingSeconds %= 3600;
            long minutes = remainingSeconds / 60;
            // Nur Stunden und Minuten anzeigen
            editBdtButton.setMessage(Text.literal(String.format("Wartezeit: %02dh %02dm", hours, minutes)).formatted(Formatting.RED));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        updateEditButtonState(); // Stelle sicher, dass der Button-Status gerendert wird

        if (!statusText.getString().isEmpty()) {
            int feedbackY = this.height - 50;
            if (closeButton != null) {
                feedbackY = Math.min(feedbackY, closeButton.getY() - this.textRenderer.fontHeight - 5);
            }
            context.drawCenteredTextWithShadow(this.textRenderer, statusText, this.width / 2, feedbackY, 0xFFFFFF);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(null);
        else super.close();
    }
}