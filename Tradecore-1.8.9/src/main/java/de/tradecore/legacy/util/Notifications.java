package de.tradecore.legacy.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

public final class Notifications {
    private Notifications() {}

    public static void info(final String message) {
        ClientTasks.run(new Runnable() {
            @Override public void run() {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.thePlayer != null) {
                    mc.thePlayer.addChatMessage(new ChatComponentText(
                        EnumChatFormatting.GOLD + "[Tradecore] " + EnumChatFormatting.RESET + message));
                }
            }
        });
    }

    public static void error(String message) {
        info(EnumChatFormatting.RED + message);
    }
}
