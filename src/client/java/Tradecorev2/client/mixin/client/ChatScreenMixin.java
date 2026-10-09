package Tradecorev2.client.mixin.client;

import Tradecorev2.client.ChatCalculator;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({ChatScreen.class})
public class ChatScreenMixin {
   @Shadow
   protected EditBox input;

   @Inject(
      method = {"keyPressed"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
      if (event.key() == InputConstants.KEY_TAB && this.input != null) {
         String currentText = this.input.getValue();
         if (ChatCalculator.isMathExpression(currentText)) {
            String result = ChatCalculator.evaluate(currentText);
            if (result != null) {
               this.input.setValue(currentText + " " + result);
               this.input.moveCursorTo(this.input.getValue().length(), false);
               cir.setReturnValue(true);
            }
         }
      }

   }
}
