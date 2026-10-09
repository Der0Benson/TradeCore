package Tradecorev2.client;

import Tradecorev2.client.config.ConfigManager;
import Tradecorev2.client.config.ModConfig;
import Tradecorev2.client.gui.BdtScreen;
import Tradecorev2.client.gui.CalculatorScreen;
import Tradecorev2.client.gui.SettingsScreen;
import Tradecorev2.client.gui.WelcomeScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public class Tradecorev2Client implements ClientModInitializer {
   private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("tradecorev2", "main"));
   private static KeyMapping settingsKey;
   private static KeyMapping bdtKey;
   private static KeyMapping calculatorKey;
   private static KeyMapping blockHighlightKey;
   private boolean hasShownDiscordMessage;
   private boolean hasLoadedBdtForSession;

   @Override
   public void onInitializeClient() {
      ConfigManager.initialize();
      UpdateChecker.checkForUpdatesAsync();
      BdtCache.initializeOnGameStart();

      settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.tradecorev2.open_settings", InputConstants.KEY_UP, KEY_CATEGORY));
      bdtKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.tradecorev2.open_bdt", InputConstants.KEY_RIGHT, KEY_CATEGORY));
      calculatorKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.tradecorev2.open_calculator", InputConstants.KEY_Z, KEY_CATEGORY));
      blockHighlightKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.tradecorev2.block_highlight", InputConstants.KEY_DOWN, KEY_CATEGORY));

      ClientTickEvents.END_CLIENT_TICK.register(client -> {
         if (client.player == null) {
            this.hasLoadedBdtForSession = false;
            return;
         }

         ModConfig config = ConfigManager.getConfig();
         if (!config.welcomeShown && client.gui.screen() == null) {
            client.gui.setScreen(new WelcomeScreen(null));
         }

         if (!this.hasLoadedBdtForSession) {
            this.hasLoadedBdtForSession = true;
            BdtFetcher.fetchBdtFromApiAsync(client.player.getUUID().toString(), null, true);
         }

         if (!this.hasShownDiscordMessage) {
            this.showDiscordToast(client);
            this.hasShownDiscordMessage = true;
         }

         UpdateChecker.tryShowPendingToastIfPossible(client);

         while (settingsKey.consumeClick()) {
            client.gui.setScreen(new SettingsScreen());
         }

         while (bdtKey.consumeClick()) {
            this.openBdt(client);
         }

         while (calculatorKey.consumeClick()) {
            if (client.hasShiftDown()) {
               client.gui.setScreen(new CalculatorScreen());
            }
         }

         while (blockHighlightKey.consumeClick()) {
            BlockHighlighter.toggleHighlighting();
         }

         BlockHighlighter.tick(client);
      });

   }

   private void openBdt(Minecraft client) {
      boolean forceRefresh = BdtFetcher.shouldRefreshLocalData();
      BdtFetcher.fetchBdtFromApiAsync(
         client.player.getUUID().toString(),
         () -> client.execute(() -> client.gui.setScreen(new BdtScreen())),
         forceRefresh
      );
   }

   private void showDiscordToast(Minecraft client) {
      client.gui.toastManager().addToast(new SystemToast(
         SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
         Component.literal("TradeCore Community").withStyle(ChatFormatting.GOLD),
         Component.literal("Tritt unserem Discord bei! Den Knopf findest du in den Einstellungen.")
      ));
   }

}
