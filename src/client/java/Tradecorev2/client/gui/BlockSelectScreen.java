package Tradecorev2.client.gui;

import Tradecorev2.client.gui.BlockSelectScreen.BlockListWidget.Entry;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BlockSelectScreen extends Screen {
   private final Screen parent;
   private final Consumer<List<Selection>> onSelect;
   private final SelectionMode selectionMode;
   private final EntrySource entrySource;
   private static final int ROW_HEIGHT = 20;
   private static final int ICON_SIZE = 16;
   private static final int PANEL_MARGIN = 15;
   private static final int BG_COLOR = -804253680;
   private static final int BORDER_COLOR = -1;
   private static final int HOVER_BG = 822083583;
   private static final int SELECT_BG = 1090519039;
   private static final int PREVIEW_SPACING = 10;
   private static final int MOB_PREVIEW_SIZE = 32;
   private static final AtomicInteger NEXT_PREVIEW_ENTITY_ID = new AtomicInteger(-1);
   private EditBox searchField;
   private BlockListWidget listWidget;
   private Button selectButton;
   private int panelX;
   private int panelY;
   private int panelWidth;
   private int panelHeight;
   private int previewY;
   private int listX;
   private int listY;
   private int listWidth;
   private int listHeight;
   private BlockListWidget.@Nullable Entry selectedEntry;
   private final Set<BlockListWidget.Entry> selectedEntries;
   private final boolean useMobPreview3d;
   private @Nullable LivingEntity previewEntity;
   private String previewEntityId;

   public BlockSelectScreen(Screen parent, BiConsumer<String, String> onSelect) {
      this(parent, BlockSelectScreen.SelectionMode.SINGLE, (selections) -> {
         if (!selections.isEmpty()) {
            Selection selection = (Selection)selections.get(0);
            onSelect.accept(selection.blockId, selection.displayName);
         }
      });
   }

   public BlockSelectScreen(Screen parent, SelectionMode selectionMode, Consumer<List<Selection>> onSelect) {
      this(parent, selectionMode, selectionMode == BlockSelectScreen.SelectionMode.MULTI ? BlockSelectScreen.EntrySource.ITEMS : BlockSelectScreen.EntrySource.BLOCKS, onSelect);
   }

   public BlockSelectScreen(Screen parent, SelectionMode selectionMode, EntrySource entrySource, Consumer<List<Selection>> onSelect) {
      this(parent, selectionMode, entrySource, false, onSelect);
   }

   public BlockSelectScreen(Screen parent, SelectionMode selectionMode, EntrySource entrySource, boolean useMobPreview3d, Consumer<List<Selection>> onSelect) {
      super(Component.literal(getTitleFor(entrySource)));
      this.selectedEntries = new LinkedHashSet();
      this.parent = parent;
      this.selectionMode = selectionMode == null ? BlockSelectScreen.SelectionMode.SINGLE : selectionMode;
      this.entrySource = entrySource == null ? BlockSelectScreen.EntrySource.BLOCKS : entrySource;
      this.onSelect = onSelect;
      this.useMobPreview3d = useMobPreview3d;
   }

   protected void init() {
      super.init();
      this.panelWidth = Math.min(this.width - 30, 420);
      this.panelWidth = Math.max(this.panelWidth, 280);
      this.panelX = (this.width - this.panelWidth) / 2;
      this.panelY = 18;
      this.panelHeight = this.height - this.panelY - 36;
      int searchWidth = this.panelWidth - 40;
      int searchX = this.panelX + (this.panelWidth - searchWidth) / 2;
      int searchY = this.panelY + 18;
      this.previewY = searchY + 32;
      this.searchField = new EditBox(this.font, searchX, searchY, searchWidth, 20, Component.literal(this.getSearchPlaceholder()));
      this.searchField.setResponder((s) -> this.refreshList());
      this.addRenderableWidget(this.searchField);
      this.setInitialFocus(this.searchField);
      int listTop = this.previewY + 44 + 10;
      int selectY = this.panelY + this.panelHeight - 30;
      int listHeight = selectY - listTop - 24;
      if (listHeight < 40) {
         listHeight = 40;
      }

      this.listX = this.panelX;
      this.listY = listTop;
      this.listWidth = this.panelWidth;
      this.listHeight = listHeight;
      this.listWidget = new BlockListWidget(this.minecraft, this.panelWidth, listHeight, listTop, 20);
      this.listWidget.setX(this.panelX);
      this.addRenderableWidget(this.listWidget);
      this.selectButton = Button.builder(this.getSelectButtonText(), (button) -> {
         List<Selection> selections = this.getSelections();
         if (!selections.isEmpty()) {
            this.onSelect.accept(selections);
            if (Minecraft.getInstance().gui.screen() == this) {
               this.onClose();
            }
         }
      }).bounds(this.panelX + (this.panelWidth - 140) / 2, selectY, 140, 20).build();
      this.updateSelectionState();
      this.addRenderableWidget(this.selectButton);
      this.populateList("");
   }

   private void refreshList() {
      this.populateList(this.searchField.getValue());
   }

   private void populateList(String filter) {
      if (this.listWidget != null) {
         String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
         List<BlockListWidget.Entry> entries = new ArrayList();
         if (this.entrySource == BlockSelectScreen.EntrySource.ITEMS) {
            this.addItemEntries(entries, needle);
         } else if (this.entrySource == BlockSelectScreen.EntrySource.MOBS) {
            this.addMobEntries(entries, needle);
         } else if (this.entrySource == BlockSelectScreen.EntrySource.BLOCKS_AND_MOBS) {
            this.addBlockEntries(entries, needle);
            this.addMobEntries(entries, needle);
            entries.sort(Comparator.comparing((entry) -> entry.displayName, String.CASE_INSENSITIVE_ORDER));
         } else {
            this.addBlockEntries(entries, needle);
         }

         this.listWidget.replaceEntries(entries);
         this.listWidget.setScrollAmount((double)0.0F);
         this.selectedEntry = null;
         this.selectedEntries.clear();
         this.clearPreviewEntity();
         this.updateSelectionState();
      }
   }

   private void addItemEntries(List<BlockListWidget.Entry> entries, String needle) {
      BuiltInRegistries.ITEM.stream().filter((item) -> item != Items.AIR).sorted(Comparator.comparing((item) -> item.getName(item.getDefaultInstance()).getString())).forEach((item) -> {
         Identifier id = BuiltInRegistries.ITEM.getKey(item);
         String displayName = item.getName(item.getDefaultInstance()).getString();
         String idStr = id.toString();
         String displayLower = displayName.toLowerCase(Locale.ROOT);
         String idLower = idStr.toLowerCase(Locale.ROOT);
         if (needle.isEmpty() || displayLower.contains(needle) || idLower.contains(needle)) {
            entries.add(this.listWidget.new Entry(item, idStr, displayName));
         }

      });
   }

   private void addBlockEntries(List<BlockListWidget.Entry> entries, String needle) {
      BuiltInRegistries.BLOCK.stream().sorted(Comparator.comparing((block) -> block.getName().getString())).forEach((block) -> {
         Identifier id = BuiltInRegistries.BLOCK.getKey(block);
         String displayName = block.getName().getString();
         String idStr = id.toString();
         String displayLower = displayName.toLowerCase(Locale.ROOT);
         String idLower = idStr.toLowerCase(Locale.ROOT);
         if (needle.isEmpty() || displayLower.contains(needle) || idLower.contains(needle)) {
            Item item = block.asItem();
            entries.add(this.listWidget.new Entry(item, idStr, displayName));
         }

      });
   }

   private void addMobEntries(List<BlockListWidget.Entry> entries, String needle) {
      BuiltInRegistries.ENTITY_TYPE.stream().sorted(Comparator.comparing((entityType) -> entityType.getDescription().getString())).forEach((entityType) -> {
         Item eggItem = SpawnEggItem.byId(entityType).map(holder -> holder.value()).orElse(null);
         if (eggItem != null) {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
            String displayName = entityType.getDescription().getString();
            String idStr = id.toString();
            String displayLower = displayName.toLowerCase(Locale.ROOT);
            String idLower = idStr.toLowerCase(Locale.ROOT);
            if (needle.isEmpty() || displayLower.contains(needle) || idLower.contains(needle)) {
               entries.add(this.listWidget.new Entry(eggItem, idStr, displayName));
            }

         }
      });
   }

   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      context.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, -804253680);
      context.outline(this.panelX, this.panelY, this.panelWidth, this.panelHeight, -1);
      int titleY = this.panelY + 6;
      context.centeredText(this.font, this.title, this.width / 2, titleY, -1);
      int selectionCount = this.selectedEntries.size();
      if (selectionCount == 1) {
         BlockListWidget.Entry previewEntry = this.selectedEntry != null ? this.selectedEntry : this.selectedEntries.iterator().next();
         int previewCenter = this.panelX + this.panelWidth / 2;
         boolean renderedMob = false;
         if (this.useMobPreview3d && this.isMobEntry(previewEntry)) {
            LivingEntity entity = this.getOrCreatePreviewEntity(previewEntry.blockId);
            if (entity != null) {
               int boxTop = this.previewY - 2;
               int boxLeft = previewCenter - 16;
               int boxRight = boxLeft + 32;
               int boxBottom = boxTop + 32;
               InventoryScreen.extractEntityInInventoryFollowsMouse(context, boxLeft, boxTop, boxRight, boxBottom, 32, 0.0625F, (float)mouseX, (float)mouseY, entity);
               context.centeredText(this.font, Component.literal(previewEntry.displayName), previewCenter, boxBottom + 2, -1);
               renderedMob = true;
            }
         }

         if (!renderedMob) {
            ItemStack stack = previewEntry.item.getDefaultInstance();
            if (!stack.isEmpty()) {
               context.item(stack, previewCenter - 8, this.previewY);
            }

            context.centeredText(this.font, Component.literal(previewEntry.displayName), previewCenter, this.previewY + 20, -1);
         }
      } else if (selectionCount > 1) {
         int previewCenter = this.panelX + this.panelWidth / 2;
         context.centeredText(this.font, Component.literal(selectionCount + " " + this.getPluralLabel() + " ausgewählt").withStyle(ChatFormatting.YELLOW), previewCenter, this.previewY + 12, -1);
      } else {
         int previewCenter = this.panelX + this.panelWidth / 2;
         context.centeredText(this.font, Component.literal("Kein " + this.getSingularLabel() + " ausgewählt").withStyle(ChatFormatting.GRAY), previewCenter, this.previewY + 8, -1);
      }

      context.fill(this.panelX + 8, this.previewY + 44, this.panelX + this.panelWidth - 8, this.previewY + 45, 1358954495);
      super.extractRenderState(context, mouseX, mouseY, delta);
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      double mouseX = event.x();
      double mouseY = event.y();
      int button = event.button();
      if (this.selectButton != null && this.selectButton.isMouseOver(mouseX, mouseY)) {
         return this.selectButton.active ? this.selectButton.mouseClicked(event, doubleClick) : true;
      } else if (this.searchField != null && this.searchField.isMouseOver(mouseX, mouseY)) {
         return super.mouseClicked(event, doubleClick);
      } else if (this.listWidget != null && this.isInsideList(mouseX, mouseY)) {
         boolean allowSelect = true;
         if (mouseX < (double)this.listWidget.getScrollbarPositionX()) {
            BlockListWidget.Entry hit = this.listWidget.entryAt(mouseX, mouseY);
            if (hit == null || !this.isInsideEntryContent(mouseX, hit)) {
               allowSelect = false;
            }
         } else {
            allowSelect = false;
         }

         if (!this.listWidget.mouseClicked(event, doubleClick)) {
            return true;
         } else {
            this.setFocused(this.listWidget);
            BlockListWidget.Entry entry = this.listWidget.entryAt(mouseX, mouseY);
            if (allowSelect && entry != null && button == InputConstants.MOUSE_BUTTON_LEFT) {
               boolean confirmDoubleClick = this.selectedEntry == entry && this.selectionMode == BlockSelectScreen.SelectionMode.SINGLE;
               this.toggleSelection(entry);
               if (confirmDoubleClick) {
                  List<Selection> selections = this.getSelections();
                  if (!selections.isEmpty()) {
                     this.onSelect.accept(selections);
                     if (Minecraft.getInstance().gui.screen() != this) {
                        return true;
                     }

                     this.onClose();
                  }
               }
            }

            return true;
         }
      } else {
         return this.isInsidePanel(mouseX, mouseY) ? true : super.mouseClicked(event, doubleClick);
      }
   }

   public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
      return this.listWidget != null && this.listWidget.mouseDragged(event, deltaX, deltaY) ? true : super.mouseDragged(event, deltaX, deltaY);
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      return this.listWidget != null && this.listWidget.mouseReleased(event) ? true : super.mouseReleased(event);
   }

   public void onClose() {
      Minecraft.getInstance().gui.setScreen(this.parent);
   }

   private boolean isInsidePanel(double mouseX, double mouseY) {
      return mouseX >= (double)this.panelX && mouseX <= (double)(this.panelX + this.panelWidth) && mouseY >= (double)this.panelY && mouseY <= (double)(this.panelY + this.panelHeight);
   }

   private boolean isInsideList(double mouseX, double mouseY) {
      return mouseX >= (double)this.listX && mouseX <= (double)(this.listX + this.listWidth) && mouseY >= (double)this.listY && mouseY <= (double)(this.listY + this.listHeight);
   }

   private boolean isInsideEntryContent(double mouseX, BlockListWidget.Entry entry) {
      int rowLeft = this.listX + (this.listWidth - this.listWidget.getRowWidth()) / 2;
      int textWidth = Minecraft.getInstance().font.width(entry.displayName);
      int clickableRight = rowLeft + 16 + 6 + textWidth + 4;
      return mouseX <= (double)clickableRight;
   }

   private void toggleSelection(BlockListWidget.Entry entry) {
      if (this.selectionMode == BlockSelectScreen.SelectionMode.MULTI) {
         if (this.selectedEntries.contains(entry)) {
            this.selectedEntries.remove(entry);
         } else {
            this.selectedEntries.add(entry);
         }

         if (this.selectedEntries.contains(entry)) {
            this.selectedEntry = entry;
         } else if (this.selectedEntries.isEmpty()) {
            this.selectedEntry = null;
         } else {
            this.selectedEntry = this.selectedEntries.iterator().next();
         }
      } else {
         this.selectedEntries.clear();
         if (entry != null) {
            this.selectedEntries.add(entry);
            this.selectedEntry = entry;
         } else {
            this.selectedEntry = null;
         }
      }

      if (this.selectedEntries.size() != 1) {
         this.clearPreviewEntity();
      }

      this.updateSelectionState();
   }

   private void updateSelectionState() {
      int count = this.selectedEntries.size();
      if (this.selectButton != null) {
         this.selectButton.active = count > 0;
         this.selectButton.setMessage(this.getSelectButtonText());
      }

   }

   private Component getSelectButtonText() {
      int count = this.selectedEntries.size();
      return this.selectionMode == BlockSelectScreen.SelectionMode.MULTI ? Component.literal("Auswählen (" + count + ")") : Component.literal("Auswählen");
   }

   private List<Selection> getSelections() {
      if (this.selectedEntries.isEmpty()) {
         return Collections.emptyList();
      } else {
         List<Selection> selections = new ArrayList();

         for(BlockListWidget.Entry entry : this.selectedEntries) {
            selections.add(new Selection(entry.blockId, entry.displayName));
         }

         return selections;
      }
   }

   private boolean isMobEntry(BlockListWidget.Entry entry) {
      Identifier identifier = Identifier.tryParse(entry.blockId);
      return identifier != null && BuiltInRegistries.ENTITY_TYPE.containsKey(identifier);
   }

   private @Nullable LivingEntity getOrCreatePreviewEntity(String id) {
      if (id != null && !id.isBlank()) {
         if (this.previewEntity != null && id.equals(this.previewEntityId)) {
            return this.previewEntity;
         } else {
            this.previewEntity = null;
            this.previewEntityId = id;
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.level != null) {
               Identifier identifier = Identifier.tryParse(id);
               if (identifier != null && BuiltInRegistries.ENTITY_TYPE.containsKey(identifier)) {
                  EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(identifier);
                  Entity entity = entityType.create(client.level, EntitySpawnReason.SPAWN_ITEM_USE);
                  if (entity instanceof LivingEntity) {
                     LivingEntity living = (LivingEntity)entity;
                      // Preview entities are never added to the level, so Minecraft does not assign
                      // them a network ID. The renderer still requires a non-zero ID as its item seed.
                      living.setId(NEXT_PREVIEW_ENTITY_ID.getAndDecrement());
                     this.previewEntity = living;
                     return this.previewEntity;
                  } else {
                     return null;
                  }
               } else {
                  return null;
               }
            } else {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   private void clearPreviewEntity() {
      this.previewEntity = null;
      this.previewEntityId = null;
   }

   private static String getTitleFor(EntrySource entrySource) {
      if (entrySource == BlockSelectScreen.EntrySource.ITEMS) {
         return "Item/Block auswählen";
      } else if (entrySource == BlockSelectScreen.EntrySource.MOBS) {
         return "Mob auswählen";
      } else {
         return entrySource == BlockSelectScreen.EntrySource.BLOCKS_AND_MOBS ? "Block/Mob auswählen" : "Block auswählen";
      }
   }

   private String getSearchPlaceholder() {
      if (this.entrySource == BlockSelectScreen.EntrySource.ITEMS) {
         return "Suche Item/Block";
      } else if (this.entrySource == BlockSelectScreen.EntrySource.MOBS) {
         return "Suche Mob";
      } else {
         return this.entrySource == BlockSelectScreen.EntrySource.BLOCKS_AND_MOBS ? "Suche Block oder Mob" : "Suche Block";
      }
   }

   private String getSingularLabel() {
      if (this.entrySource == BlockSelectScreen.EntrySource.ITEMS) {
         return "Item";
      } else if (this.entrySource == BlockSelectScreen.EntrySource.MOBS) {
         return "Mob";
      } else {
         return this.entrySource == BlockSelectScreen.EntrySource.BLOCKS_AND_MOBS ? "Block/Mob" : "Block";
      }
   }

   private String getPluralLabel() {
      if (this.entrySource == BlockSelectScreen.EntrySource.ITEMS) {
         return "Items";
      } else if (this.entrySource == BlockSelectScreen.EntrySource.MOBS) {
         return "Mobs";
      } else {
         return this.entrySource == BlockSelectScreen.EntrySource.BLOCKS_AND_MOBS ? "Blöcke/Mobs" : "Blöcke";
      }
   }

   @Environment(EnvType.CLIENT)
   public static enum SelectionMode {
      SINGLE,
      MULTI
   }

   @Environment(EnvType.CLIENT)
   public static enum EntrySource {
      BLOCKS,
      ITEMS,
      MOBS,
      BLOCKS_AND_MOBS
   }

   @Environment(EnvType.CLIENT)
   public static final class Selection {
      public final String blockId;
      public final String displayName;

      private Selection(String blockId, String displayName) {
         this.blockId = blockId;
         this.displayName = displayName;
      }
   }

   @Environment(EnvType.CLIENT)
   class BlockListWidget extends ContainerObjectSelectionList<BlockListWidget.Entry> {
      public BlockListWidget(Minecraft client, int width, int height, int top, int itemHeight) {
         super(client, width, height, top, itemHeight);
      }

      public int getRowWidth() {
         return this.width - 12;
      }

      protected int getScrollbarPositionX() {
         return this.getX() + this.width - 6;
      }

      public Entry entryAt(double mouseX, double mouseY) {
         return (Entry)this.getEntryAtPosition(mouseX, mouseY);
      }

      @Environment(EnvType.CLIENT)
      class Entry extends ContainerObjectSelectionList.Entry<Entry> {
         private final Item item;
         private final String blockId;
         private final String displayName;

         Entry(Item item, String blockId, String displayName) {
            this.item = item;
            this.blockId = blockId;
            this.displayName = displayName;
         }

         public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int x = this.getContentX();
            int y = this.getY();
            int entryWidth = this.getContentWidth();
            int entryHeight = this.getHeight();
            boolean selected = BlockSelectScreen.this.selectedEntries.contains(this);
            int color = selected ? -16384 : (hovered ? -24576 : -1);
            if (hovered || selected) {
               int bg = selected ? 1090519039 : 822083583;
               context.fill(x - 2, y, x + entryWidth + 2, y + entryHeight - 2, bg);
            }

            ItemStack stack = this.item.getDefaultInstance();
            int iconY = y + (entryHeight - 16) / 2;
            int textX = x + 16 + 6;
            int firstLineY = y + 2;
            if (!stack.isEmpty()) {
               context.item(stack, x + 2, iconY);
            }

            context.text(Minecraft.getInstance().font, this.displayName, textX, firstLineY, color, false);
         }

         public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            return true;
         }

         public List<? extends GuiEventListener> children() {
            return Collections.emptyList();
         }

         public List<? extends NarratableEntry> narratables() {
            return Collections.emptyList();
         }
      }
   }
}
