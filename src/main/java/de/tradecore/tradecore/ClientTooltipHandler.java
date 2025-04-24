package de.tradecore.tradecore;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.screen.Screen; // NEU: Import für Screen
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class ClientTooltipHandler implements ClientModInitializer {
    private static KeyBinding openCacheInfoKey;

    @Override
    public void onInitializeClient() {
        // Tastenkombination registrieren... (unverändert)
        openCacheInfoKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tradecore.open_cache_info",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UP,
                "category.tradecore"
        ));

        // Tooltip-Handler
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            // NEU: Prüfen, ob Preise angezeigt werden sollen
            // Bedingungen:
            // 1. Einstellung ist "Immer anzeigen" ODER
            // 2. Einstellung ist "Nur bei Shift" UND die Shift-Taste ist gedrückt
            boolean shouldShowPrices = !TradeCore.showPricesOnlyOnShift || Screen.hasShiftDown();

            if (shouldShowPrices && stack != null && !stack.isEmpty()) { // Füge shouldShowPrices zur Bedingung hinzu
                try {
                    String itemId = stack.getItem().toString();
                    PriceAPIClient.PriceResult priceResult = TradeCore.apiClient.getItemPrices(itemId);

                    if (priceResult != null) {
                        lines.add(Text.literal("Stackpreis: " + priceResult.stackpreis + "$").formatted(Formatting.GREEN));
                        lines.add(Text.literal("DK-Preis: " + priceResult.dkpreis + "$").formatted(Formatting.GREEN));
                    } else {
                        // Optional: Nur "Unbekannt" anzeigen, wenn Shift gedrückt wird? Oder immer?
                        // Aktuell wird es nur angezeigt, wenn die Hauptbedingung (shouldShowPrices) erfüllt ist.
                        lines.add(Text.literal("Preis: Unbekannt").formatted(Formatting.GRAY));
                    }
                } catch (Exception e) {
                    // Fehler werden ebenfalls nur angezeigt, wenn die Hauptbedingung erfüllt ist.
                    lines.add(Text.literal("Fehler beim Abrufen des Preises").formatted(Formatting.RED));
                    TradeCore.LOGGER.error("Fehler beim Abrufen des Item-Preises: ", e);
                }
            }
        });

        // Tastendruck-Ereignis registrieren... (unverändert)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openCacheInfoKey.wasPressed() && client.player != null) {
                // Stelle sicher, dass Minecraft und der Client nicht null sind
                if (client.currentScreen == null) {
                    client.setScreen(new CacheInfoScreen());
                }
            }
        });
    }
}