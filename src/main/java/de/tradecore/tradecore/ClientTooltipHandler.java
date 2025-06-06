package de.tradecore.tradecore;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
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
        TradeCore.LOGGER.info("ClientTooltipHandler: Registriere Keybindings und Handlers...");

        openCacheInfoKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tradecore.open_cache_info", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UP, "category.tradecore"
        ));
        openPriceSubmitKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tradecore.open_price_submit", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_DOWN, "category.tradecore"
        ));
        openBdtKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tradecore.open_bdt", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, "category.tradecore"
        ));

        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (!TradeCoreConfig.modEnabled) {
                return;
            }
            if (TradeCore.apiClient == null) return;
            boolean shouldShowPrices = !TradeCoreConfig.showPricesOnlyOnShift || Screen.hasShiftDown();
            if (shouldShowPrices && stack != null && !stack.isEmpty()) {
                try {
                    String itemId = Registries.ITEM.getId(stack.getItem()).toString();
                    PriceAPIClient.PriceResult priceResult = TradeCore.apiClient.getItemPrices(itemId);
                    if (priceResult != null) {
                        if (priceResult.stueckpreis > 0) {
                            lines.add(Text.literal("Stückpreis: " + priceResult.stueckpreis + "$").formatted(Formatting.GREEN));
                        }
                        if (priceResult.stackpreis > 0) {
                            lines.add(Text.literal("Stackpreis: " + priceResult.stackpreis + "$").formatted(Formatting.GREEN));
                        }
                        if (priceResult.dkpreis > 0) {
                            lines.add(Text.literal("DK-Preis: " + priceResult.dkpreis + "$").formatted(Formatting.GREEN));
                        }
                    } else {
                        lines.add(Text.literal("Preis: Unbekannt").formatted(Formatting.GRAY));
                    }
                } catch (Exception e) {
                    lines.add(Text.literal("Fehler beim Preisabruf").formatted(Formatting.RED));
                }
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && !TradeCoreConfig.tutorialShown && client.currentScreen == null) {
                client.setScreen(new TutorialScreen());
            }

            if (client.player == null) return;

            if (client.currentScreen == null) {
                if (openCacheInfoKey.wasPressed()) {
                    client.setScreen(new CacheInfoScreen());
                } else if (TradeCoreConfig.modEnabled) {
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
                }
                // Der fehlerhafte "else"-Block wurde hier entfernt.
            }
        });
        TradeCore.LOGGER.info("Client Handlers registriert.");
    }
}