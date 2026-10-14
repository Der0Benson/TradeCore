package de.tradecore.legacy;

import de.tradecore.legacy.config.ConfigManager;
import de.tradecore.legacy.event.ClientEvents;
import de.tradecore.legacy.highlight.BlockHighlighter;
import de.tradecore.legacy.service.BdtService;
import de.tradecore.legacy.service.UpdateService;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.lwjgl.input.Keyboard;

@Mod(modid = TradecoreMod.MOD_ID, name = TradecoreMod.NAME, version = TradecoreMod.VERSION,
    acceptedMinecraftVersions = "[1.8.9]", clientSideOnly = true)
public final class TradecoreMod {
    public static final String MOD_ID = "tradecore";
    public static final String NAME = "Tradecore";
    public static final String VERSION = "2.2.13-1.8.9";
    public static final String KEY_CATEGORY = "key.categories.tradecore";

    public static KeyBinding settingsKey;
    public static KeyBinding bdtKey;
    public static KeyBinding calculatorKey;
    public static KeyBinding highlightKey;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ConfigManager.initialize(event.getModConfigurationDirectory());
        BdtService.loadCache();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        settingsKey = register("key.tradecore.open_settings", Keyboard.KEY_UP);
        bdtKey = register("key.tradecore.open_bdt", Keyboard.KEY_RIGHT);
        calculatorKey = register("key.tradecore.open_calculator", Keyboard.KEY_Z);
        highlightKey = register("key.tradecore.toggle_highlight", Keyboard.KEY_DOWN);

        ClientEvents events = new ClientEvents();
        MinecraftForge.EVENT_BUS.register(events);
        MinecraftForge.EVENT_BUS.register(BlockHighlighter.INSTANCE);
        FMLCommonHandler.instance().bus().register(events);
        UpdateService.checkAsync();
    }

    private static KeyBinding register(String description, int keyCode) {
        KeyBinding binding = new KeyBinding(description, keyCode, KEY_CATEGORY);
        ClientRegistry.registerKeyBinding(binding);
        return binding;
    }
}
