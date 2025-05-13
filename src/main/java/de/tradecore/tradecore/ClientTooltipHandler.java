package de.tradecore.tradecore;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback; // NEU
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class ClientTooltipHandler implements ClientModInitializer {
    private static KeyBinding openCacheInfoKey;
    private static KeyBinding openPriceSubmitKey;
    private static KeyBinding openBdtKey;

    @Override
    public void onInitializeClient() {
        TradeCore.LOGGER.info("ClientTooltipHandler: Registriere Keybindings, Handlers und HUD Overlay...");

        openCacheInfoKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tradecore.open_cache_info", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UP, "category.tradecore"
        ));
        openPriceSubmitKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tradecore.open_price_submit", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_DOWN, "category.tradecore"
        ));
        openBdtKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tradecore.open_bdt",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT,
                "category.tradecore"
        ));

        // NEU: Registriere das HUD Overlay
        HudRenderCallback.EVENT.register(new TradeCoreHudOverlay());

        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            // NEU: Prüfe, ob die Mod überhaupt aktiviert ist
            if (!TradeCoreConfig.modEnabled) {
                return; // Wenn deaktiviert, keine Preis-Tooltips anzeigen
            }

            if (TradeCore.apiClient == null) return;
            boolean shouldShowPrices = !TradeCoreConfig.showPricesOnlyOnShift || Screen.hasShiftDown();
            if (shouldShowPrices && stack != null && !stack.isEmpty()) {
                try {
                    String itemId = Registries.ITEM.getId(stack.getItem()).toString();
                    PriceAPIClient.PriceResult priceResult = TradeCore.apiClient.getItemPrices(itemId);
                    if (priceResult != null) {
                        lines.add(Text.literal("Stackpreis: " + priceResult.stackpreis + "$").formatted(Formatting.GREEN));
                        lines.add(Text.literal("DK-Preis: " + priceResult.dkpreis + "$").formatted(Formatting.GREEN));
                    } else {
                        lines.add(Text.literal("Preis: Unbekannt").formatted(Formatting.GRAY));
                    }
                } catch (Exception e) {
                    lines.add(Text.literal("Fehler beim Preisabruf").formatted(Formatting.RED));
                    String failedItemId = "unknown";
                    try {
                        failedItemId = Registries.ITEM.getId(stack.getItem()).toString();
                    } catch (Exception idEx) {
                        // ignore
                    }
                    TradeCore.LOGGER.error("Tooltip Preisabruf Fehler für {}: ", failedItemId, e);
                }
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (TradeCore.apiClient == null) return; // Sollte nicht passieren, aber sicher ist sicher
            if (openCacheInfoKey == null || openPriceSubmitKey == null || openBdtKey == null) return;

            // Der CacheInfoKey sollte immer funktionieren, um die Mod ggf. wieder einzuschalten
            if (client.currentScreen == null && client.player != null) {
                if (openCacheInfoKey.wasPressed()) {
                    client.setScreen(new CacheInfoScreen());
                }
                // NEU: Andere Keybindings nur ausführen, wenn die Mod aktiviert ist
                else if (TradeCoreConfig.modEnabled) {
                    if (openPriceSubmitKey.wasPressed()) {
                        ItemStack heldItem = client.player.getMainHandStack();
                        if (!heldItem.isEmpty()) {
                            client.setScreen(new PriceSubmissionScreen(heldItem));
                        } else {
                            client.player.sendMessage(Text.literal("Du musst ein Item in der Hand halten!").formatted(Formatting.YELLOW), false);
                        }
                    } else if (openBdtKey.wasPressed()) {
                        client.setScreen(new BlockOfTheDayScreen());
                    }
                } else {
                    // Optional: Feedback geben, wenn versucht wird, eine Funktion der deaktivierten Mod zu nutzen
                    if (openPriceSubmitKey.wasPressed() || openBdtKey.wasPressed()) {
                        if (TradeCoreConfig.lastModStatusChangeTime == 0 || // Verhindert Spam, wenn Banner gerade aktiv war
                                System.currentTimeMillis() - TradeCoreConfig.lastModStatusChangeTime > TradeCoreConfig.BANNER_DISPLAY_DURATION_MS) {
                            TradeCoreConfig.lastModStatusChangeTime = System.currentTimeMillis(); // Trigger Banner
                        }
                        // Die Nachricht im Banner reicht hier aus. Zusätzliche Chat-Nachricht wäre zu viel.
                    }
                }
            }
        });
        TradeCore.LOGGER.info("Client Handlers und HUD Overlay registriert.");
    }
}