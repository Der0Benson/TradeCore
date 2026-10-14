package de.tradecore.legacy.highlight;

import de.tradecore.legacy.config.ConfigManager;
import de.tradecore.legacy.model.BdtData;
import de.tradecore.legacy.service.BdtService;
import de.tradecore.legacy.util.Notifications;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumParticleTypes;

public final class BlockHighlighter {
    public static final BlockHighlighter INSTANCE = new BlockHighlighter();
    private static final int MAX_RESULTS = 384;
    private final List<BlockPos> positions = new ArrayList<BlockPos>();
    private boolean enabled;
    private Block target;
    private int targetMetadata = -1;
    private BlockPos origin;
    private int x;
    private int y;
    private int z;
    private int radius;
    private boolean scanning;
    private long nextScanAt;
    private int particleTick;

    private BlockHighlighter() {}

    public void toggle() {
        if (enabled) {
            enabled = false; scanning = false; positions.clear();
            Notifications.info("BdT-Highlight deaktiviert.");
            return;
        }
        BdtData data = BdtService.getCurrent();
        if (data == null || data.item_id == null) {
            Notifications.error("Kein Block des Tages geladen.");
            return;
        }
        target = resolveBlock(data.item_id);
        if (target == null) {
            Notifications.error("Dieser BdT-Block existiert in Minecraft 1.8.9 nicht: " + data.item_id);
            return;
        }
        enabled = true;
        beginScan();
        Notifications.info("BdT-Highlight aktiviert: " + data.item_name);
    }

    private Block resolveBlock(String id) {
        String blockId = id;
        int metadataSeparator = blockId.indexOf('#');
        targetMetadata = -1;
        if (metadataSeparator >= 0) {
            try { targetMetadata = Integer.parseInt(blockId.substring(metadataSeparator + 1)); }
            catch (NumberFormatException ignored) {}
            blockId = blockId.substring(0, metadataSeparator);
        }
        Block block = Block.getBlockFromName(blockId);
        if (block == null && blockId.indexOf(':') < 0) block = Block.getBlockFromName("minecraft:" + blockId.toLowerCase().replace(' ', '_'));
        return block;
    }

    public void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!enabled || mc.theWorld == null || mc.thePlayer == null || target == null) return;
        if (!scanning && System.currentTimeMillis() >= nextScanAt) beginScan();
        if (scanning) scanStep(mc);
        if (++particleTick % 6 == 0) emitParticles(mc);
    }

    private void beginScan() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        radius = ConfigManager.get().highlightDistance;
        origin = new BlockPos(mc.thePlayer);
        x = -radius;
        z = -radius;
        y = Math.max(0, origin.getY() - radius);
        positions.clear();
        scanning = true;
    }

    private void scanStep(Minecraft mc) {
        int checked = 0;
        int minY = Math.max(0, origin.getY() - radius);
        int maxY = Math.min(255, origin.getY() + radius);
        while (scanning && checked++ < 6000) {
            BlockPos pos = new BlockPos(origin.getX() + x, y, origin.getZ() + z);
            if (x * x + z * z <= radius * radius && mc.theWorld.isBlockLoaded(pos)
                && matchesTarget(mc, pos) && !fullySurrounded(mc, pos)) {
                positions.add(pos);
            }
            y++;
            if (y > maxY) { y = minY; z++; }
            if (z > radius) { z = -radius; x++; }
            if (x > radius) finishScan();
        }
    }

    private boolean matchesTarget(Minecraft mc, BlockPos pos) {
        if (mc.theWorld.getBlockState(pos).getBlock() != target) return false;
        return targetMetadata < 0 || target.getMetaFromState(mc.theWorld.getBlockState(pos)) == targetMetadata;
    }

    private boolean fullySurrounded(Minecraft mc, BlockPos pos) {
        return mc.theWorld.getBlockState(pos.up()).getBlock().isFullBlock()
            && mc.theWorld.getBlockState(pos.down()).getBlock().isFullBlock()
            && mc.theWorld.getBlockState(pos.north()).getBlock().isFullBlock()
            && mc.theWorld.getBlockState(pos.south()).getBlock().isFullBlock()
            && mc.theWorld.getBlockState(pos.east()).getBlock().isFullBlock()
            && mc.theWorld.getBlockState(pos.west()).getBlock().isFullBlock();
    }

    private void finishScan() {
        scanning = false;
        Collections.sort(positions, new Comparator<BlockPos>() {
            @Override public int compare(BlockPos a, BlockPos b) {
                return Double.compare(a.distanceSq(origin), b.distanceSq(origin));
            }
        });
        if (positions.size() > MAX_RESULTS) positions.subList(MAX_RESULTS, positions.size()).clear();
        nextScanAt = System.currentTimeMillis() + ConfigManager.get().highlightRescanSeconds * 1000L;
    }

    public void clear() { positions.clear(); scanning = false; }

    private void emitParticles(Minecraft mc) {
        int rgb = parseColor(ConfigManager.get().highlightColorHex);
        double red = Math.max(0.001D, ((rgb >> 16) & 255) / 255D);
        double green = ((rgb >> 8) & 255) / 255D;
        double blue = (rgb & 255) / 255D;
        int amount = ConfigManager.get().highlightParticleAmount;
        int levels = 5 + amount * 3;
        int limit = Math.min(48, positions.size());
        for (int index = 0; index < limit; index++) {
            BlockPos pos = positions.get(index);
            for (int level = 0; level < levels; level++) {
                double height = 0.15D + (9.85D * level / Math.max(1, levels - 1));
                double angle = (particleTick * 0.18D) + (index * 0.7D) + (level * 0.9D);
                double px = pos.getX() + 0.5D + Math.cos(angle) * 0.10D;
                double py = pos.getY() + height;
                double pz = pos.getZ() + 0.5D + Math.sin(angle) * 0.10D;
                mc.theWorld.spawnParticle(EnumParticleTypes.REDSTONE, px, py, pz, red, green, blue);
            }
        }
    }

    private static int parseColor(String value) {
        try { return Integer.parseInt(value.substring(1), 16); }
        catch (Exception ignored) { return 0xFFAA00; }
    }
}
