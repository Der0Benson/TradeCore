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
import net.minecraft.registry.Registries; // Korrekter Import für Item-Registry
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class ClientTooltipHandler implements ClientModInitializer {
    private static KeyBinding openCacheInfoKey;
    private static KeyBinding openPriceSubmitKey;

    @Override
    public void onInitializeClient() {
        // Keybinding für Cache-Info (Pfeil Hoch)
        openCacheInfoKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tradecore.open_cache_info", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UP, "category.tradecore"
        ));

        // Keybinding für Preisvorschlag (Pfeil Runter)
        openPriceSubmitKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tradecore.open_price_submit", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_DOWN, "category.tradecore"
        ));

        // Tooltip-Handler
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            boolean shouldShowPrices = !TradeCore.showPricesOnlyOnShift || Screen.hasShiftDown();
            if (shouldShowPrices && stack != null && !stack.isEmpty()) {
                try {
                    // Hole die Item-ID aus der Registry (zuverlässiger als toString())
                    String itemId = Registries.ITEM.getId(stack.getItem()).toString();
                    PriceAPIClient.PriceResult priceResult = TradeCore.apiClient.getItemPrices(itemId);

                    if (priceResult != null) {
                        lines.add(Text.literal("Stackpreis: " + priceResult.stackpreis + "$").formatted(Formatting.GREEN));
                        lines.add(Text.literal("DK-Preis: " + priceResult.dkpreis + "$").formatted(Formatting.GREEN));
                    } else {
                        lines.add(Text.literal("Preis: Unbekannt").formatted(Formatting.GRAY));
                    }
                } catch (Exception e) {
                    lines.add(Text.literal("Fehler beim Abrufen des Preises").formatted(Formatting.RED));
                    TradeCore.LOGGER.error("Fehler beim Abrufen des Item-Preises für {}: ", stack.getItem().toString(), e);
                }
            }
        });

        // Tastendruck-Ereignis registrieren
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.currentScreen == null && client.player != null) {
                if (openCacheInfoKey.wasPressed()) {
                    client.setScreen(new CacheInfoScreen());
                } else if (openPriceSubmitKey.wasPressed()) {
                    ItemStack heldItem = client.player.getMainHandStack();
                    if (heldItem != null && !heldItem.isEmpty()) {
                        client.setScreen(new PriceSubmissionScreen(heldItem));
                    } else {
                        client.player.sendMessage(Text.literal("Du musst ein Item in der Hand halten!").formatted(Formatting.YELLOW), false);
                    }
                }
            }
        });
    }
}