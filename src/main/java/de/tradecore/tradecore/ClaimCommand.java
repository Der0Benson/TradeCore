package de.tradecore.tradecore;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.concurrent.CompletableFuture;

public class ClaimCommand {

    private static final String API_CLAIM_URL = "https://mc-tradecore.de/API/UUIDsubmit.php";

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("TC-claim")
                .then(CommandManager.argument("discordId", StringArgumentType.string())
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                            String discordId = StringArgumentType.getString(context, "discordId");
                            return executeClaim(context.getSource(), player, discordId);
                        })
                )
                .executes(context -> {
                    context.getSource().sendError(Text.literal("Bitte gib deine Discord-ID an. Beispiel: /TC-claim 123456723452345"));
                    return 0; // Fehler, da keine Discord-ID angegeben wurde
                })
        );
    }

    private static int executeClaim(ServerCommandSource source, ServerPlayerEntity player, String discordId) {
        String playerUuid = player.getUuidAsString();
        String minecraftName = player.getName().getString();

        source.sendFeedback(() -> Text.literal("Versuche, deine Discord-ID ('" + discordId + "') mit deinem Minecraft-Account ('" + minecraftName + "') zu verknüpfen...").formatted(Formatting.YELLOW), false);

        // Führe den API-Aufruf asynchron aus, um den Server nicht zu blockieren
        CompletableFuture.runAsync(() -> {
            try {
                if (TradeCore.apiClient == null) {
                    source.sendError(Text.literal("Fehler: Der API-Client ist nicht initialisiert. Bitte kontaktiere einen Admin.").formatted(Formatting.RED));
                    return;
                }
                // Hier rufst du eine neue Methode in deinem PriceAPIClient auf
                PriceAPIClient.ClaimResult result = TradeCore.apiClient.submitClaimRequest(playerUuid, minecraftName, discordId);

                if (result.success) {
                    source.sendFeedback(() -> Text.literal("Erfolg! Deine Discord-ID wurde erfolgreich mit deinem Account verknüpft.").formatted(Formatting.GREEN), false);
                    if (result.message != null && !result.message.isEmpty()) {
                        source.sendFeedback(() -> Text.literal("Server-Nachricht: " + result.message).formatted(Formatting.AQUA), false);
                    }
                } else {
                    String errorMessage = result.message != null && !result.message.isEmpty() ? result.message : "Unbekannter Fehler.";
                    source.sendError(Text.literal("Fehler beim Verknüpfen: " + errorMessage).formatted(Formatting.RED));
                }

            } catch (Exception e) {
                TradeCore.LOGGER.error("Fehler beim Ausführen des Claim-Befehls für Spieler {} mit Discord-ID {}:", minecraftName, discordId, e);
                source.sendError(Text.literal("Ein interner Fehler ist aufgetreten. Bitte versuche es später erneut oder kontaktiere einen Admin.").formatted(Formatting.RED));
            }
        });

        return 1; // Erfolg (Befehl wurde angenommen, Ergebnis kommt asynchron)
    }
}