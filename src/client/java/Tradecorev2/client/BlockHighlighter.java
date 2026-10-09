package Tradecorev2.client;

import Tradecorev2.client.config.ConfigManager;
import Tradecorev2.client.util.BdtNameUtil;
import Tradecorev2.client.util.HighlightColorUtil;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

@Environment(EnvType.CLIENT)
public class BlockHighlighter {
   private static final Set<BlockPos> highlightedPositions = new HashSet();
   private static final Set<BlockPos> pendingHighlightedPositions = new HashSet();
   private static final PriorityQueue<BlockPos> nearestScanPositions = new PriorityQueue<>(Comparator.comparingLong(BlockHighlighter::distanceFromScanOriginSquared).reversed());
   private static final int MAX_HIGHLIGHTED_POSITIONS = 128;
   private static final int MAX_PARTICLE_COLUMNS = 96;
   private static final int PARTICLE_INTERVAL_TICKS = 5;
   private static Set<BlockPos> scanOutputPositions;
   private static Block highlightedBlockType;
   private static boolean isHighlighting;
   private static boolean isResolving;
   private static DustParticleOptions cachedHighlightParticle;
   private static String cachedHexColor;
   private static long nextRescanTick;
   private static long nextParticleTick;
   private static final BlockPos.MutableBlockPos scanPos;
   private static final int MIN_SCAN_POSITIONS_PER_TICK = 10000;
   private static final int MAX_SCAN_POSITIONS_PER_TICK = 80000;
   private static final int TARGET_SCAN_TICKS_INITIAL = 20;
   private static final int TARGET_SCAN_TICKS_RESCAN = 60;
   private static int scanPositionsPerTick;
   private static boolean scanInProgress;
   private static Block scanTargetBlock;
   private static int scanRadius;
   private static int scanRadiusSq;
   private static int scanChunkRadius;
   private static int scanOriginX;
   private static int scanOriginY;
   private static int scanOriginZ;
   private static int scanOriginChunkX;
   private static int scanOriginChunkZ;
   private static int scanMinY;
   private static int scanMaxY;
   private static int scanChunkOffsetX;
   private static int scanChunkOffsetZ;
   private static int scanX;
   private static int scanY;
   private static int scanZ;
   private static int scanChunkStartX;
   private static int scanChunkStartZ;
   private static boolean scanChunkLoaded;

   private BlockHighlighter() {
   }

   public static void toggleHighlighting() {
      Minecraft client = Minecraft.getInstance();
      if (client.level != null && client.player != null) {
         if (isHighlighting) {
            clearHighlighting();
         } else if (!isResolving) {
            isResolving = true;
            BdtFetcher.fetchBdtFromApiAsync(client.player.getUUID().toString(), () -> highlightBdtBlock(client), true);
         }
      }
   }

   private static void highlightBdtBlock(Minecraft client) {
      BdtData bdtData = BdtCache.getBdtData();
      if (bdtData != null && hasText(bdtData.item_id, bdtData.item_name)) {
         resolveAndHighlightBlock(client, bdtData.item_id, bdtData.item_name);
      } else {
         isResolving = false;
         sendMessage(client, "Kein BdT gesetzt.", ChatFormatting.RED);
      }
   }

   private static void resolveAndHighlightBlock(Minecraft client, String itemId, String itemName) {
      Block localBlock = BdtNameUtil.resolveBlock(itemId);
      if (localBlock == null) {
         localBlock = BdtNameUtil.resolveBlock(itemName);
      }
      if (localBlock != null) {
         startHighlightingForBlock(client, localBlock);
         isResolving = false;
      } else {
         String resolverInput = itemName != null && !itemName.isBlank() ? itemName : itemId;
         BdtResolver.resolveAsync(resolverInput, (result) -> {
            try {
               if (result != null && result.success) {
                  Block resolvedBlock = BdtNameUtil.resolveBlock(result.resolved_id);
                  if (resolvedBlock == null) {
                     resolvedBlock = BdtNameUtil.resolveBlock(result.resolved_name);
                  }
                  if (resolvedBlock != null) {
                     startHighlightingForBlock(client, resolvedBlock);
                     return;
                  }
               }

               sendMessage(client, "Dieser BdT ist kein hervorhebbarer Block.", ChatFormatting.RED);
            } finally {
               isResolving = false;
            }

         });
      }
   }

   private static boolean hasText(String... values) {
      for (String value : values) {
         if (value != null && !value.isBlank()) {
            return true;
         }
      }
      return false;
   }

   private static void startHighlightingForBlock(Minecraft client, Block targetBlock) {
      highlightedBlockType = targetBlock;
      beginScan(client, targetBlock);
      scanStep(client);
      isHighlighting = true;
      if (client.level != null) {
         nextRescanTick = client.level.getGameTime() + getRescanIntervalTicks();
         nextParticleTick = 0L;
      }

      sendMessage(client, "Block-Highlighting aktiviert.", ChatFormatting.GREEN);
   }

   private static void beginScan(Minecraft client, Block targetBlock) {
      if (client.level != null && client.player != null) {
         scanTargetBlock = targetBlock;
         scanRadius = getHighlightDistance();
         scanRadiusSq = scanRadius * scanRadius;
         BlockPos playerPos = client.player.blockPosition();
         scanOriginX = playerPos.getX();
         scanOriginY = playerPos.getY();
         scanOriginZ = playerPos.getZ();
         scanOriginChunkX = scanOriginX >> 4;
         scanOriginChunkZ = scanOriginZ >> 4;
         scanChunkRadius = Math.max(1, (int)Math.ceil((double)scanRadius / (double)16.0F));
         int bottomY = client.level.getMinY();
         int topY = client.level.getMaxY();
         scanMinY = Math.max(bottomY, scanOriginY - scanRadius);
         scanMaxY = Math.min(topY, scanOriginY + scanRadius);
         if (scanMinY > scanMaxY) {
            scanInProgress = false;
         } else {
            scanChunkOffsetX = -scanChunkRadius;
            scanChunkOffsetZ = -scanChunkRadius;
            scanX = 0;
            scanY = scanMinY;
            scanZ = 0;
            boolean initialScan = highlightedPositions.isEmpty();
            scanOutputPositions = initialScan ? highlightedPositions : pendingHighlightedPositions;
            scanPositionsPerTick = computeScanPositionsPerTick(initialScan, scanMaxY - scanMinY + 1);
            scanOutputPositions.clear();
            nearestScanPositions.clear();
            scanInProgress = true;
            updateChunkBase(client);
         }
      } else {
         scanInProgress = false;
      }
   }

   private static int computeScanPositionsPerTick(boolean initialScan, int height) {
      int chunkSide = scanChunkRadius * 2 + 1;
      long totalPositions = (long)chunkSide * (long)chunkSide * 256L * (long)height;
      int targetTicks = initialScan ? 20 : 60;
      long perTick = (totalPositions + (long)targetTicks - 1L) / (long)targetTicks;
      if (perTick < 10000L) {
         perTick = 10000L;
      }

      if (perTick > 80000L) {
         perTick = 80000L;
      }

      return (int)perTick;
   }

   private static void scanStep(Minecraft client) {
      if (scanInProgress && scanTargetBlock != null && client.level != null) {
         int processed = 0;

         while(scanInProgress && processed < scanPositionsPerTick) {
            if (!scanChunkLoaded) {
               advanceChunk(client);
            } else {
               int x = scanChunkStartX + scanX;
               int y = scanY;
               int z = scanChunkStartZ + scanZ;
               int dx = x - scanOriginX;
               int dy = y - scanOriginY;
               int dz = z - scanOriginZ;
               if (dx * dx + dy * dy + dz * dz <= scanRadiusSq) {
                  scanPos.set(x, y, z);
                  BlockState state = client.level.getBlockState(scanPos);
                  if (state.getBlock() == scanTargetBlock && !isFullySurrounded(client.level, scanPos)) {
                     offerNearestPosition(scanPos.immutable());
                  }
               }

               ++processed;
               advancePosition(client);
            }
         }

      } else {
         scanInProgress = false;
      }
   }

   private static void advancePosition(Minecraft client) {
      ++scanZ;
      if (scanZ >= 16) {
         scanZ = 0;
         ++scanY;
         if (scanY > scanMaxY) {
            scanY = scanMinY;
            ++scanX;
            if (scanX >= 16) {
               scanX = 0;
               advanceChunk(client);
            }
         }
      }
   }

   private static void advanceChunk(Minecraft client) {
      ++scanChunkOffsetZ;
      if (scanChunkOffsetZ > scanChunkRadius) {
         scanChunkOffsetZ = -scanChunkRadius;
         ++scanChunkOffsetX;
      }

      if (scanChunkOffsetX > scanChunkRadius) {
         finishScan();
      } else {
         scanX = 0;
         scanY = scanMinY;
         scanZ = 0;
         updateChunkBase(client);
      }
   }

   private static void updateChunkBase(Minecraft client) {
      if (client.level == null) {
         scanChunkLoaded = false;
      } else {
         int chunkX = scanOriginChunkX + scanChunkOffsetX;
         int chunkZ = scanOriginChunkZ + scanChunkOffsetZ;
         scanChunkStartX = chunkX << 4;
         scanChunkStartZ = chunkZ << 4;
         scanChunkLoaded = client.level.hasChunk(chunkX, chunkZ);
      }
   }

   private static void finishScan() {
      scanOutputPositions.clear();
      scanOutputPositions.addAll(nearestScanPositions);
      nearestScanPositions.clear();
      if (scanOutputPositions == pendingHighlightedPositions) {
         highlightedPositions.clear();
         highlightedPositions.addAll(pendingHighlightedPositions);
         pendingHighlightedPositions.clear();
      }

      scanOutputPositions = highlightedPositions;
      scanInProgress = false;
   }

   private static boolean isFullySurrounded(Level world, BlockPos pos) {
      for(Direction direction : Direction.values()) {
         BlockPos neighborPos = pos.relative(direction);
         BlockState neighborState = world.getBlockState(neighborPos);
         if (neighborState.isAir()) {
            return false;
         }

         if (!neighborState.isCollisionShapeFullBlock(world, neighborPos)) {
            return false;
         }
      }

      return true;
   }

   public static void clearHighlighting() {
      highlightedPositions.clear();
      pendingHighlightedPositions.clear();
      highlightedBlockType = null;
      isHighlighting = false;
      isResolving = false;
      nextRescanTick = 0L;
      nextParticleTick = 0L;
      scanInProgress = false;
      scanTargetBlock = null;
      nearestScanPositions.clear();
      Minecraft client = Minecraft.getInstance();
      sendMessage(client, "Block-Highlighting deaktiviert.", ChatFormatting.RED);
   }

   public static boolean isHighlighting() {
      return isHighlighting;
   }

   public static Set<BlockPos> getHighlightedPositions() {
      return highlightedPositions;
   }

   public static Block getHighlightedBlockType() {
      return highlightedBlockType;
   }

   public static DustParticleOptions getHighlightParticle() {
      String normalized = HighlightColorUtil.normalizeHex(ConfigManager.getConfig().highlightColorHex);
      if (cachedHighlightParticle == null || !normalized.equals(cachedHexColor)) {
         cachedHexColor = normalized;
         int rgb = Integer.parseInt(normalized.substring(1), 16);
         cachedHighlightParticle = new DustParticleOptions(rgb, 1.0F);
      }

      return cachedHighlightParticle;
   }

   public static int getParticleAmount() {
      int amount = ConfigManager.getConfig().highlightParticleAmount;
      if (amount < 1) {
         return 1;
      } else {
         return amount > 5 ? 5 : amount;
      }
   }

   public static void tick(Minecraft client) {
      if (isHighlighting && highlightedBlockType != null) {
         if (client.level != null && client.player != null) {
            if (scanInProgress) {
               scanStep(client);
            } else {
               long currentTick = client.level.getGameTime();
               if (currentTick >= nextRescanTick) {
                  nextRescanTick = currentTick + getRescanIntervalTicks();
                  beginScan(client, highlightedBlockType);
                  scanStep(client);
               }
            }

            long currentTick = client.level.getGameTime();
            if (currentTick >= nextParticleTick) {
               nextParticleTick = currentTick + PARTICLE_INTERVAL_TICKS;
               emitHighlightParticles(client, currentTick);
            }
         }
      }
   }

   private static void offerNearestPosition(BlockPos position) {
      if (nearestScanPositions.size() < MAX_HIGHLIGHTED_POSITIONS) {
         nearestScanPositions.offer(position);
         return;
      }

      BlockPos farthest = nearestScanPositions.peek();
      if (farthest != null && distanceFromScanOriginSquared(position) < distanceFromScanOriginSquared(farthest)) {
         nearestScanPositions.poll();
         nearestScanPositions.offer(position);
      }
   }

   private static long distanceFromScanOriginSquared(BlockPos position) {
      long dx = (long)position.getX() - scanOriginX;
      long dy = (long)position.getY() - scanOriginY;
      long dz = (long)position.getZ() - scanOriginZ;
      return dx * dx + dy * dy + dz * dz;
   }

   private static void emitHighlightParticles(Minecraft client, long currentTick) {
      if (client.level == null) {
         return;
      }

      DustParticleOptions particle = getHighlightParticle();
      int particleAmount = getParticleAmount();
      double heightOffset = 1.0D + (currentTick / PARTICLE_INTERVAL_TICKS % 19L) * 0.4D;
      int rendered = 0;
      for (BlockPos pos : highlightedPositions) {
         if (rendered++ >= MAX_PARTICLE_COLUMNS) {
            break;
         }

         BlockState state = client.level.getBlockState(pos);
         if (state.getBlock() != highlightedBlockType) {
            continue;
         }

         double x = pos.getX() + 0.5D;
         double y = pos.getY() + heightOffset;
         double z = pos.getZ() + 0.5D;
         client.level.addParticle(particle, x, y, z, 0.0D, 0.01D, 0.0D);
         for (int i = 1; i < particleAmount; ++i) {
            double angle = (currentTick + i * 7L) * 0.35D;
            client.level.addParticle(particle, x + Math.cos(angle) * 0.2D, y, z + Math.sin(angle) * 0.2D, 0.0D, 0.01D, 0.0D);
         }
      }
   }

   private static long getRescanIntervalTicks() {
      int seconds = ConfigManager.getConfig().highlightRescanSeconds;
      if (seconds < 1) {
         seconds = 1;
      }

      return (long)seconds * 20L;
   }

   private static int getHighlightDistance() {
      int distance = ConfigManager.getConfig().highlightDistance;
      if (distance < 8) {
         return 8;
      } else {
         return distance > 128 ? 128 : distance;
      }
   }

   private static MutableComponent prefix() {
      return Component.literal("[").withStyle(ChatFormatting.DARK_GRAY).append(Component.literal("TradeCore").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD})).append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY)).append(Component.literal(" "));
   }

   private static void sendMessage(Minecraft client, String message, ChatFormatting color) {
      if (client.player != null) {
         client.player.sendSystemMessage(prefix().append(Component.literal(message).withStyle(color)));
      }
   }

   static {
      scanOutputPositions = highlightedPositions;
      highlightedBlockType = null;
      isHighlighting = false;
      isResolving = false;
      cachedHexColor = "";
      nextRescanTick = 0L;
      nextParticleTick = 0L;
      scanPos = new BlockPos.MutableBlockPos();
      scanPositionsPerTick = 10000;
      scanInProgress = false;
      scanTargetBlock = null;
   }
}
