package Tradecorev2.client.gui;

import Tradecorev2.client.BdtCreator;
import Tradecorev2.client.BdtFetcher;
import Tradecorev2.client.BdtResolver;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class CreateBdtScreen extends Screen {
   private static final Logger LOGGER = LoggerFactory.getLogger("TradecoreV2-BdtCreateScreen");
   private static final AtomicInteger NEXT_PREVIEW_ENTITY_ID = new AtomicInteger(-1);
   private EditBox gewinnField;
   private Button createButton;
   private Button pickBlockButton;
   private int labelX;
   private boolean isResolving = false;
   private String statusMessage = "";
   private String selectedBlockId = "";
   private String selectedBlockName = "";
   private ItemStack selectedStack;
   private LivingEntity previewEntity;
   private String previewEntityId;

   public CreateBdtScreen() {
      super(Component.literal("Neuen BdT-Eintrag erstellen"));
      this.selectedStack = ItemStack.EMPTY;
   }

   protected void init() {
      super.init();
      int panelWidth = 320;
      int panelHeight = 300;
      int startX = (this.width - panelWidth) / 2;
      int startY = (this.height - panelHeight) / 2;
      this.labelX = startX + 20;
      int inputX = startX + 110;
      int inputWidth = 180;
      int fieldY = startY + 74;
      this.gewinnField = new EditBox(this.font, inputX, fieldY, inputWidth, 22, Component.literal("Gewinnbeschreibung"));
      this.gewinnField.addFormatter((string, firstCharacterIndex) -> Component.literal(string)
         .withStyle(this.gewinnField.getValue().trim().isEmpty() ? ChatFormatting.RED : ChatFormatting.WHITE)
         .getVisualOrderText());
      this.gewinnField.setResponder((text) -> this.validateInputs());
      this.addRenderableWidget(this.gewinnField);
      this.setInitialFocus(this.gewinnField);
      this.pickBlockButton = Button.builder(Component.literal("Block/Mob auswählen"), (button) -> this.openBlockPicker()).bounds(startX + (panelWidth - 180) / 2, startY + 114, 180, 22).build();
      this.addRenderableWidget(this.pickBlockButton);
      this.createButton = Button.builder(Component.literal("BdT prüfen"), (button) -> this.onCreate()).bounds(startX + (panelWidth - 160) / 2, startY + panelHeight - 34, 160, 22).build();
      this.addRenderableWidget(this.createButton);
      this.validateInputs();
   }

   private void validateInputs() {
      boolean itemValid = !this.selectedBlockId.trim().isEmpty();
      boolean gewinnValid = !this.gewinnField.getValue().trim().isEmpty();
      this.createButton.active = itemValid && gewinnValid && !this.isResolving;
   }

   private void onCreate() {
      if (this.createButton.active && !this.isResolving) {
         if (this.selectedBlockId.isEmpty()) {
            this.statusMessage = "Bitte zuerst einen Block oder Mob auswählen.";
            this.validateInputs();
         } else {
            String gewinn = this.gewinnField.getValue().trim();
            LOGGER.info("BdT submit triggered with entry {} and reward '{}'", this.selectedBlockId, gewinn);
            this.isResolving = true;
            this.statusMessage = "Prüfe Eingabe bei der API...";
            this.validateInputs();
            BdtResolver.resolveAsync(this.selectedBlockId, (result) -> this.handleResolveResult(result, this.selectedBlockId, gewinn));
         }
      }
   }

   private void handleResolveResult(BdtResolver.ResolveResult result, String originalInput, String gewinn) {
      this.isResolving = false;
      this.statusMessage = "";
      this.validateInputs();
      if (result != null && result.success && result.resolved_name != null && !result.resolved_name.isBlank()) {
         Minecraft client = Minecraft.getInstance();
         if (client != null) {
            LOGGER.info("BdT resolve success: name='{}' id='{}'", result.resolved_name, result.resolved_id);
            client.gui.setScreen(new ResolveBdtConfirmScreen(this, originalInput, result.resolved_name, result.resolved_id, () -> {
               String payloadId = firstNonBlank(result.resolved_id, result.resolved_name, this.selectedBlockId);
               String payloadName = firstNonBlank(result.resolved_name, this.selectedBlockName, payloadId);
               if (payloadId != null && !payloadId.isBlank()) {
                  LOGGER.info("BdT creating with id='{}', name='{}', reward='{}'", new Object[]{payloadId, payloadName, gewinn});
                  this.isResolving = true;
                  this.statusMessage = "BdT wird erstellt...";
                  this.validateInputs();
                  BdtCreator.createBdtAsync(payloadId, payloadName, gewinn, createResult -> {
                     Minecraft currentClient = Minecraft.getInstance();
                     if (!createResult.success()) {
                        this.isResolving = false;
                        this.statusMessage = createResult.message();
                        this.validateInputs();
                        return;
                     }

                     this.statusMessage = "BdT wurde erstellt. Daten werden geladen...";
                     if (currentClient != null && currentClient.player != null) {
                        BdtFetcher.fetchBdtFromApiAsync(currentClient.player.getUUID().toString(), () -> currentClient.gui.setScreen(new BdtScreen()), true);
                     } else if (currentClient != null) {
                        currentClient.gui.setScreen(new BdtScreen());
                     }
                  });

               } else {
                  LOGGER.error("BdT submit aborted: no resolved id/name available");
                  this.statusMessage = "Kein ID-Ergebnis von der API.";
                  this.validateInputs();
               }
            }));
         }
      } else {
         String message = result != null && result.message != null && !result.message.isBlank() ? result.message : "BdT konnte nicht verifiziert werden.";
         this.showToast("BdT-Eingabe", message);
      }

   }

   private void openBlockPicker() {
      Minecraft client = Minecraft.getInstance();
      if (client != null) {
         client.gui.setScreen(new BlockSelectScreen(this, BlockSelectScreen.SelectionMode.SINGLE, BlockSelectScreen.EntrySource.BLOCKS_AND_MOBS, true, (selections) -> {
            if (!selections.isEmpty()) {
               BlockSelectScreen.Selection selection = (BlockSelectScreen.Selection)selections.get(0);
               this.selectedBlockId = selection.blockId;
               this.selectedBlockName = selection.displayName;
               this.selectedStack = this.createStackFromId(selection.blockId);
               this.updatePreviewEntity(selection.blockId);
               LOGGER.info("BdT selection set to {} ({})", this.selectedBlockName, this.selectedBlockId);
               this.setFocused(this.gewinnField);
               this.gewinnField.setFocused(true);
               this.statusMessage = "";
               this.validateInputs();
            }
         }));
      }
   }

   private ItemStack createStackFromId(String id) {
      try {
         Identifier identifier = Identifier.parse(id);
         if (BuiltInRegistries.BLOCK.containsKey(identifier)) {
            return ((Block)BuiltInRegistries.BLOCK.getValue(identifier)).asItem().getDefaultInstance();
         } else if (BuiltInRegistries.ITEM.containsKey(identifier)) {
            return ((Item)BuiltInRegistries.ITEM.getValue(identifier)).getDefaultInstance();
         } else {
            return BuiltInRegistries.ENTITY_TYPE.containsKey(identifier) ? ItemStack.EMPTY : ItemStack.EMPTY;
         }
      } catch (Exception var3) {
         return ItemStack.EMPTY;
      }
   }

   private void updatePreviewEntity(String id) {
      this.previewEntity = null;
      this.previewEntityId = null;
      if (id != null && !id.isBlank()) {
         Minecraft client = Minecraft.getInstance();
         if (client != null && client.level != null) {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier != null && BuiltInRegistries.ENTITY_TYPE.containsKey(identifier)) {
               EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(identifier);
               Entity entity = entityType.create(client.level, EntitySpawnReason.SPAWN_ITEM_USE);
               if (entity instanceof LivingEntity) {
                  LivingEntity living = (LivingEntity)entity;
                   // This entity is rendered only in the GUI and is never added to the level,
                   // so Minecraft will not assign the non-zero ID required by its renderer.
                   living.setId(NEXT_PREVIEW_ENTITY_ID.getAndDecrement());
                  this.previewEntity = living;
                  this.previewEntityId = id;
               }

            }
         }
      }
   }

   private void showToast(String title, String description) {
      Minecraft client = Minecraft.getInstance();
      if (client != null) {
         client.gui.toastManager().addToast(new SystemToast(SystemToastId.PERIODIC_NOTIFICATION, Component.literal(title), Component.literal(description)));
      }
   }

   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      int panelWidth = 320;
      int panelHeight = 300;
      int startX = (this.width - panelWidth) / 2;
      int startY = (this.height - panelHeight) / 2;
      context.fill(startX, startY, startX + panelWidth, startY + panelHeight, -534765536);
      context.outline(startX, startY, panelWidth, panelHeight, -1);
      Component titleText = this.getTitle();
      int titleWidth = this.font.width(titleText);
      context.text(this.font, titleText, startX + (panelWidth - titleWidth) / 2, startY + 8, -1);
      int labelY = startY + 74 + 4;
      context.text(this.font, "Gewinn:", this.labelX, labelY, 0xFFA0A0A0);
      int previewCenterX = startX + panelWidth / 2;
      int iconY = startY + 172;
      context.centeredText(this.font, Component.literal("Ausgewählter Eintrag"), previewCenterX, iconY - 26, 0xFFA0A0A0);
      if (!this.selectedBlockId.isEmpty()) {
         if (this.previewEntity == null && this.isEntityId(this.selectedBlockId)) {
            this.updatePreviewEntity(this.selectedBlockId);
         }

         boolean renderedMob = false;
         if (this.previewEntity != null && this.selectedBlockId.equals(this.previewEntityId)) {
            int boxLeft = previewCenterX - 16;
            int boxTop = iconY - 2;
            int boxRight = boxLeft + 32;
            int boxBottom = boxTop + 32;
            InventoryScreen.extractEntityInInventoryFollowsMouse(context, boxLeft, boxTop, boxRight, boxBottom, 32, 0.0625F, (float)mouseX, (float)mouseY, this.previewEntity);
            renderedMob = true;
         }

         if (!renderedMob && !this.selectedStack.isEmpty()) {
            context.item(this.selectedStack, previewCenterX - 8, iconY);
         }

         context.centeredText(this.font, Component.literal(this.selectedBlockName), previewCenterX, iconY + 20, -1);
         context.centeredText(this.font, Component.literal(this.selectedBlockId).withStyle(ChatFormatting.GRAY), previewCenterX, iconY + 32, -1);
      } else {
         context.centeredText(this.font, Component.literal("Kein Eintrag ausgewählt").withStyle(ChatFormatting.GRAY), previewCenterX, iconY + 6, -1);
      }

      if (!this.statusMessage.isEmpty()) {
         int statusWidth = this.font.width(this.statusMessage);
         context.text(this.font, this.statusMessage, startX + (panelWidth - statusWidth) / 2, startY + panelHeight - 50, 0xFFA0A0A0);
      }

      super.extractRenderState(context, mouseX, mouseY, delta);
   }

   public boolean isPauseScreen() {
      return false;
   }

   private static String firstNonBlank(String... values) {
      if (values == null) {
         return null;
      } else {
         for(String v : values) {
            if (v != null && !v.isBlank()) {
               return v;
            }
         }

         return null;
      }
   }

   private boolean isEntityId(String id) {
      if (id != null && !id.isBlank()) {
         Identifier identifier = Identifier.tryParse(id);
         return identifier != null && BuiltInRegistries.ENTITY_TYPE.containsKey(identifier);
      } else {
         return false;
      }
   }

   public boolean charTyped(CharacterEvent event) {
      return this.gewinnField.charTyped(event) ? true : super.charTyped(event);
   }

   public boolean keyPressed(KeyEvent event) {
      return this.gewinnField.keyPressed(event) ? true : super.keyPressed(event);
   }
}
