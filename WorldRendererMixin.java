package Tradecorev2.mixin.client;

import Tradecorev2.client.BlockHighlighter;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.render.WorldRenderer.class)
public class WorldRendererMixin {

    /**
     * Beacon Beam Implementierung fuer Block-Highlighting
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void tradecorev2(net.minecraft.client.util.ObjectAllocator objectAllocator,
                                                 net.minecraft.client.render.RenderTickCounter renderTickCounter,
                                                 boolean renderBlockOutline,
                                                 net.minecraft.client.render.Camera camera,
                                                 net.minecraft.client.render.GameRenderer gameRenderer,
                                                 org.joml.Matrix4f projectionMatrix,
                                                 org.joml.Matrix4f modelViewMatrix,
                                                 CallbackInfo ci) {

        if (!BlockHighlighter.isHighlighting()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return;
        }

        DustParticleEffect particle = BlockHighlighter.getHighlightParticle();

        for (BlockPos pos : BlockHighlighter.getHighlightedPositions()) {
            BlockState state = client.world.getBlockState(pos);
            if (state != null && !state.isAir()) {
                renderBeaconBeam(client, pos, particle);
            }
        }
    }

    /**
     * Rendert einen farbigen Partikelstrahl fuer den hervorgehobenen Block
     */
    private void renderBeaconBeam(MinecraftClient client, BlockPos pos, DustParticleEffect particle) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;

        if (client.world != null) {
            for (int height = 0; height < 20; height++) {
                double currentY = y + height * 0.4;
                client.world.addParticle(particle, x, currentY, z, 0.0, 0.0, 0.0);

                if (height % 5 == 0) {
                    client.world.addParticle(particle, x + 0.2, currentY, z, 0.0, 0.0, 0.0);
                    client.world.addParticle(particle, x - 0.2, currentY, z, 0.0, 0.0, 0.0);
                    client.world.addParticle(particle, x, currentY, z + 0.2, 0.0, 0.0, 0.0);
                    client.world.addParticle(particle, x, currentY, z - 0.2, 0.0, 0.0, 0.0);
                }
            }

            // Glitzer-Spitze fuer bessere Sichtbarkeit
            for (int i = 0; i < 6; i++) {
                double offsetX = (Math.random() - 0.5) * 0.4;
                double offsetZ = (Math.random() - 0.5) * 0.4;
                client.world.addParticle(particle, x + offsetX, y + 8.0 + Math.random(), z + offsetZ, 0.0, 0.02, 0.0);
            }
        }
    }
}
