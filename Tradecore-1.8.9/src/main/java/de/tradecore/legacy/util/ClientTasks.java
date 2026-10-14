package de.tradecore.legacy.util;

import net.minecraft.client.Minecraft;

public final class ClientTasks {
    private ClientTasks() {}

    public static void run(Runnable task) {
        if (task == null) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null) minecraft.addScheduledTask(task);
        else task.run();
    }
}
