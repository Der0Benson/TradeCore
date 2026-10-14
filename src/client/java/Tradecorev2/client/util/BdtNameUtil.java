package Tradecorev2.client.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;

@Environment(EnvType.CLIENT)
public final class BdtNameUtil {
   private static final Map<String, String> BLOCK_ALIASES = Map.of(
      "eisengitter", "minecraft:iron_bars",
      "sculkkatalysator", "minecraft:sculk_catalyst",
      "sculkcatalyst", "minecraft:sculk_catalyst"
   );

   private BdtNameUtil() {
   }

   public static String getDisplayName(String rawName) {
      if (rawName != null && !rawName.isBlank()) {
         Identifier id = flexibleIdentifier(rawName);
         if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
            Block block = (Block)BuiltInRegistries.BLOCK.getValue(id);
            return block.getName().getString();
         } else if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            Item item = (Item)BuiltInRegistries.ITEM.getValue(id);
            return item.getName(item.getDefaultInstance()).getString();
         } else if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(id);
            return entityType.getDescription().getString();
         } else {
            return rawName;
         }
      } else {
         return "";
      }
   }

   public static Block resolveBlock(String rawName) {
      if (rawName == null || rawName.isBlank()) {
         return null;
      }

      Identifier directId = flexibleIdentifier(rawName);
      Block directBlock = blockForId(directId);
      if (directBlock != null) {
         return directBlock;
      }

      Identifier embeddedId = embeddedIdentifier(rawName);
      Block embeddedBlock = blockForId(embeddedId);
      if (embeddedBlock != null) {
         return embeddedBlock;
      }

      String normalizedTarget = normalizeName(rawName);
      String aliasId = BLOCK_ALIASES.get(normalizedTarget);
      if (aliasId != null) {
         Identifier id = Identifier.tryParse(aliasId);
         if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
            return BuiltInRegistries.BLOCK.getValue(id);
         }
      }

      for (Block block : BuiltInRegistries.BLOCK) {
         Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
         if (normalizeName(block.getName().getString()).equals(normalizedTarget)
            || normalizeName(block.asItem().getName(block.asItem().getDefaultInstance()).getString()).equals(normalizedTarget)
            || normalizeName(blockId.getPath()).equals(normalizedTarget)
            || normalizeName(blockId.toString()).equals(normalizedTarget)) {
            return block;
         }
      }
      return null;
   }

   public static ItemStack createPreviewStack(String itemId, String itemName) {
      ItemStack stack = stackForIdentifier(flexibleIdentifier(itemId));
      if (!stack.isEmpty()) {
         return stack;
      }

      stack = stackForIdentifier(flexibleIdentifier(itemName));
      if (!stack.isEmpty()) {
         return stack;
      }

      Block block = resolveBlock(itemId);
      if (block == null) {
         block = resolveBlock(itemName);
      }

      return block == null ? ItemStack.EMPTY : block.asItem().getDefaultInstance();
   }

   private static ItemStack stackForIdentifier(Identifier id) {
      if (id == null) {
         return ItemStack.EMPTY;
      }
      if (BuiltInRegistries.BLOCK.containsKey(id)) {
         return BuiltInRegistries.BLOCK.getValue(id).asItem().getDefaultInstance();
      }
      if (BuiltInRegistries.ITEM.containsKey(id)) {
         return BuiltInRegistries.ITEM.getValue(id).getDefaultInstance();
      }
      if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
         EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(id);
         return SpawnEggItem.byId(entityType)
            .map(holder -> holder.value().getDefaultInstance())
            .orElse(ItemStack.EMPTY);
      }
      return ItemStack.EMPTY;
   }

   private static Block blockForId(Identifier id) {
      if (id == null) {
         return null;
      }
      if (BuiltInRegistries.BLOCK.containsKey(id)) {
         return BuiltInRegistries.BLOCK.getValue(id);
      }
      if (BuiltInRegistries.ITEM.containsKey(id)) {
         Item item = BuiltInRegistries.ITEM.getValue(id);
         if (item instanceof BlockItem blockItem) {
            return blockItem.getBlock();
         }
      }
      return null;
   }

   private static Identifier embeddedIdentifier(String rawName) {
      if (rawName == null || rawName.isBlank()) {
         return null;
      }

      String cleaned = rawName.trim().toLowerCase(Locale.ROOT);
      for (String part : cleaned.split("[^a-z0-9_:.\\-/]+")) {
         String candidate = part;
         if (candidate.startsWith("minecraft/")) {
            candidate = "minecraft:" + candidate.substring("minecraft/".length());
         }
         Identifier id = Identifier.tryParse(candidate);
         if (id != null && (BuiltInRegistries.BLOCK.containsKey(id) || BuiltInRegistries.ITEM.containsKey(id))) {
            return id;
         }
      }
      return null;
   }

   private static Identifier flexibleIdentifier(String rawName) {
      if (rawName == null || rawName.isBlank()) {
         return null;
      }

      String trimmed = rawName.trim().toLowerCase(Locale.ROOT);
      Identifier direct = Identifier.tryParse(trimmed);
      if (direct != null && (BuiltInRegistries.BLOCK.containsKey(direct) || BuiltInRegistries.ITEM.containsKey(direct) || BuiltInRegistries.ENTITY_TYPE.containsKey(direct))) {
         return direct;
      }

      String idCandidate = trimmed.replace(' ', '_').replace('-', '_');
      if (!idCandidate.contains(":")) {
         idCandidate = "minecraft:" + idCandidate;
      }
      return Identifier.tryParse(idCandidate);
   }

   private static String normalizeName(String value) {
      String withoutFormatting = value.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
      String germanNormalized = withoutFormatting.toLowerCase(Locale.ROOT)
         .replace("ä", "ae")
         .replace("ö", "oe")
         .replace("ü", "ue")
         .replace("ß", "ss");
      String ascii = Normalizer.normalize(germanNormalized, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
      return ascii
         .replace("minecraft:", "")
         .replaceAll("[^a-z0-9]", "");
   }
}
