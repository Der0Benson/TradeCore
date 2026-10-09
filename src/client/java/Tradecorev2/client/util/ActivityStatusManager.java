package Tradecorev2.client.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class ActivityStatusManager {
   private static volatile Status currentStatus;

   private ActivityStatusManager() {
   }

   public static Status getStatus() {
      return currentStatus;
   }

   public static void setStatus(Status status) {
      if (status != null) {
         currentStatus = status;
      }

   }

   public static Component getStatusText() {
      return currentStatus == ActivityStatusManager.Status.ACTIVE ? Component.literal("Aktiv").withStyle(ChatFormatting.GREEN) : Component.literal("Inaktiv").withStyle(ChatFormatting.RED);
   }

   static {
      currentStatus = ActivityStatusManager.Status.INACTIVE;
   }

   @Environment(EnvType.CLIENT)
   public static enum Status {
      ACTIVE,
      INACTIVE
   }
}
