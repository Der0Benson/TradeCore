package Tradecorev2.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record BdtActionResult(boolean success, String message) {
}
