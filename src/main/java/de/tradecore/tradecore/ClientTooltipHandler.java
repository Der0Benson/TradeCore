package de.tradecore.tradecore;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class ClientTooltipHandler implements ClientModInitializer {
    // Keybindings deklarieren
    private static KeyBinding openCacheInfoKey;
    private static KeyBinding openPriceSubmitKey;
    private static KeyBinding openBdtKey; // NEU

    @Override
    public void onInitializeClient() {
        // Wird früh aufgerufen
        TradeCore.LOGGER.info("ClientTooltipHandler: Registriere Keybindings und Handlers...");

        // Keybinding für Cache-Info (Pfeil Hoch)
        openCacheInfoKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tradecore.open_cache_info", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UP, "category.tradecore"
        ));
        // Keybinding für Preisvorschlag (Pfeil Runter)
        openPriceSubmitKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tradecore.open_price_submit", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_DOWN, "category.tradecore"
        ));
        // NEU: Keybinding Block des Tages (Pfeil Rechts)
        openBdtKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tradecore.open_bdt", // Neuer Key für Sprachdatei
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT, // Pfeil Rechts
                "category.tradecore"
        ));


        // Tooltip-Handler direkt hier registrieren
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (TradeCore.apiClient == null) return; // Frühzeitiger Check
            boolean shouldShowPrices = !TradeCoreConfig.showPricesOnlyOnShift || Screen.hasShiftDown();
            if (shouldShowPrices && stack != null && !stack.isEmpty()) {
                try {
                    String itemId = Registries.ITEM.getId(stack.getItem()).toString();
                    PriceAPIClient.PriceResult priceResult = TradeCore.apiClient.getItemPrices(itemId);
                    if (priceResult != null) {
                        lines.add(Text.literal("Stackpreis: " + priceResult.stackpreis + "$").formatted(Formatting.GREEN));
                        lines.add(Text.literal("DK-Preis: " + priceResult.dkpreis + "$").formatted(Formatting.GREEN));
                    } else { lines.add(Text.literal("Preis: Unbekannt").formatted(Formatting.GRAY)); }
                } catch (Exception e) {
                    lines.add(Text.literal("Fehler beim Preisabruf").formatted(Formatting.RED));
                    String failedItemId = "unknown"; try {failedItemId = Registries.ITEM.getId(stack.getItem()).toString();} catch (Exception idEx) {}
                    TradeCore.LOGGER.error("Tooltip Preisabruf Fehler für {}: ", failedItemId, e);
                }
            }
        });

        // Tastendruck-Ereignis (Tick-Listener) direkt hier registrieren
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (TradeCore.apiClient == null) return;
            // Prüfe, ob Keybindings initialisiert wurden
            if (openCacheInfoKey == null || openPriceSubmitKey == null || openBdtKey == null) return;

            if (client.currentScreen == null && client.player != null) {
                if (openCacheInfoKey.wasPressed()) {
                    client.setScreen(new CacheInfoScreen());
                } else if (openPriceSubmitKey.wasPressed()) {
                    ItemStack heldItem = client.player.getMainHandStack();
                    if (!heldItem.isEmpty()) {
                        client.setScreen(new PriceSubmissionScreen(heldItem));
                    } else {
                        client.player.sendMessage(Text.literal("Du musst ein Item in der Hand halten!").formatted(Formatting.YELLOW), false);
                    }
                } else if (openBdtKey.wasPressed()) { // NEU: Block des Tages Screen öffnen
                    client.setScreen(new BlockOfTheDayScreen());
                }
            }
        });
        TradeCore.LOGGER.info("Client Handlers registriert.");
    }
}