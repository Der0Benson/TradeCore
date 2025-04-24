package de.tradecore.tradecore;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
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
        // Tastenkombination für "-" registrieren
        openCacheInfoKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tradecore.open_cache_info",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_MINUS,
                "category.tradecore"
        ));

        // Tooltip-Handler
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (stack != null && !stack.isEmpty()) {
                try {
                    String itemId = stack.getItem().toString();
                    PriceAPIClient.PriceResult priceResult = TradeCore.apiClient.getItemPrices(itemId);

                    if (priceResult != null) {
                        lines.add(Text.literal("Stackpreis: " + priceResult.stackpreis + "$").formatted(Formatting.GREEN));
                        lines.add(Text.literal("DK-Preis: " + priceResult.dkpreis + "$").formatted(Formatting.GREEN));
                    } else {
                        lines.add(Text.literal("Preis: Unbekannt").formatted(Formatting.GRAY));
                    }
                } catch (Exception e) {
                    lines.add(Text.literal("Fehler beim Abrufen des Preises").formatted(Formatting.RED));
                    TradeCore.LOGGER.error("Fehler beim Abrufen des Item-Preises: ", e);
                }
            }
        });

        // Tastendruck-Ereignis registrieren
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openCacheInfoKey.wasPressed() && client.player != null) {
                client.setScreen(new CacheInfoScreen());
            }
        });
    }
}