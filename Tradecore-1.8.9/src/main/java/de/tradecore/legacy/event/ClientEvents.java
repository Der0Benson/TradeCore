package de.tradecore.legacy.event;

import de.tradecore.legacy.TradecoreMod;
import de.tradecore.legacy.config.ConfigManager;
import de.tradecore.legacy.gui.BdtScreen;
import de.tradecore.legacy.gui.CalculatorScreen;
import de.tradecore.legacy.gui.SettingsScreen;
import de.tradecore.legacy.gui.WelcomeScreen;
import de.tradecore.legacy.highlight.BlockHighlighter;
import de.tradecore.legacy.service.BdtService;
import de.tradecore.legacy.service.UpdateService;
import de.tradecore.legacy.util.Calculator;
import de.tradecore.legacy.util.Notifications;
import de.tradecore.legacy.util.ServerNewsFilter;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;

public final class ClientEvents {
    private boolean loadedForSession;
    private boolean discordNoticeShown;
    private Field chatInput;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            loadedForSession = false;
            BlockHighlighter.INSTANCE.clear();
            return;
        }
        if (!ConfigManager.get().welcomeShown && mc.currentScreen == null) mc.displayGuiScreen(new WelcomeScreen(null));
        if (!loadedForSession) {
            loadedForSession = true;
            BdtService.fetch(false, null);
        }
        if (!discordNoticeShown) {
            discordNoticeShown = true;
            Notifications.info("Tritt unserer Community bei! Den Discord-Knopf findest du in den Einstellungen.");
        }
        UpdateService.showPending();
        if (TradecoreMod.settingsKey.isPressed()) mc.displayGuiScreen(new SettingsScreen(mc.currentScreen));
        if (TradecoreMod.bdtKey.isPressed()) {
            BdtService.fetch(false, new Runnable() { @Override public void run() { Minecraft.getMinecraft().displayGuiScreen(new BdtScreen()); } });
        }
        if (TradecoreMod.calculatorKey.isPressed() && (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))) {
            mc.displayGuiScreen(new CalculatorScreen(mc.currentScreen));
        }
        if (TradecoreMod.highlightKey.isPressed()) BlockHighlighter.INSTANCE.toggle();
        BlockHighlighter.INSTANCE.tick();
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (event.type == 2) return;
        if (ServerNewsFilter.shouldHide(event.message.getUnformattedText())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onChatKeyboard(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!(event.gui instanceof GuiChat) || !Keyboard.getEventKeyState() || Keyboard.getEventKey() != Keyboard.KEY_TAB) return;
        try {
            if (chatInput == null) chatInput = ReflectionHelper.findField(GuiChat.class, "inputField", "field_146415_a");
            GuiTextField field = (GuiTextField) chatInput.get(event.gui);
            String text = field.getText();
            if (Calculator.isExpression(text)) {
                String result = Calculator.evaluate(text);
                if (result != null) {
                    field.setText(text + " " + result);
                    field.setCursorPositionEnd();
                    event.setCanceled(true);
                }
            }
        } catch (Exception ignored) {}
    }
}
