package de.tradecore.legacy.util;

import java.util.Locale;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public final class BdtPreview {
    private BdtPreview() {}

    public static ItemStack createStack(String rawId) {
        if (rawId == null || rawId.trim().isEmpty()) return null;
        String id = rawId.trim();
        int metadata = 0;
        int separator = id.indexOf('#');
        if (separator >= 0) {
            try { metadata = Integer.parseInt(id.substring(separator + 1)); }
            catch (NumberFormatException ignored) {}
            id = id.substring(0, separator);
        }

        if (id.indexOf(':') < 0) id = "minecraft:" + id.toLowerCase(Locale.ROOT).replace(' ', '_');
        Block block = Block.getBlockFromName(id);
        if (block != null && block != Blocks.air) {
            Item item = Item.getItemFromBlock(block);
            if (item != null) return new ItemStack(item, 1, metadata);
        }

        try {
            Object value = Item.itemRegistry.getObject(new ResourceLocation(id));
            if (value instanceof Item) return new ItemStack((Item) value, 1, metadata);
        } catch (Exception ignored) {}

        int entityId = findEntityId(id);
        return entityId < 0 ? null : new ItemStack(Items.spawn_egg, 1, entityId);
    }

    public static int findEntityId(String rawId) {
        if (rawId == null) return -1;
        String wanted = rawId.trim().toLowerCase(Locale.ROOT);
        int namespace = wanted.indexOf(':');
        if (namespace >= 0) wanted = wanted.substring(namespace + 1);
        wanted = wanted.replace("_", "").replace("-", "").replace(" ", "");
        for (Object key : EntityList.entityEggs.keySet()) {
            if (!(key instanceof Integer)) continue;
            int entityId = ((Integer) key).intValue();
            String registered = EntityList.getStringFromID(entityId);
            if (registered != null && registered.toLowerCase(Locale.ROOT).replace("_", "").equals(wanted)) return entityId;
        }
        return -1;
    }
}
