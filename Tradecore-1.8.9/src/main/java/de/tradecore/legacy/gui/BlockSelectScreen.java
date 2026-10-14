package de.tradecore.legacy.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class BlockSelectScreen extends BaseScreen {
    private final CreateBdtScreen parent;
    private final List<Entry> all = new ArrayList<Entry>();
    private final List<Entry> filtered = new ArrayList<Entry>();
    private GuiTextField search;
    private int scroll;
    private int selected = -1;
    private String lastSearch = "";
    private static final int ROW_HEIGHT = 24;
    private static final int VISIBLE_ROWS = 6;

    public BlockSelectScreen(CreateBdtScreen parent) { this.parent = parent; }

    @Override public void initGui() {
        Keyboard.enableRepeatEvents(true);
        search = new GuiTextField(1, fontRendererObj, centerX(280), 48, 280, 20);
        search.setMaxStringLength(80);
        search.setFocused(true);
        buttonList.clear();
        buttonList.add(new GuiButton(1, width / 2 - 104, height - 43, 100, 20, "Auswählen"));
        buttonList.add(new GuiButton(0, width / 2 + 4, height - 43, 100, 20, "Abbrechen"));
        if (all.isEmpty()) buildEntries();
        filter();
    }

    private void buildEntries() {
        Set<String> seen = new HashSet<String>();
        for (Object keyObject : Block.blockRegistry.getKeys()) {
            if (!(keyObject instanceof ResourceLocation)) continue;
            ResourceLocation key = (ResourceLocation) keyObject;
            Block block = Block.getBlockFromName(key.toString());
            if (block == null || block == Blocks.air) continue;
            Item item = Item.getItemFromBlock(block);
            if (item == null) continue;
            List<ItemStack> variants = new ArrayList<ItemStack>();
            try { block.getSubBlocks(item, block.getCreativeTabToDisplayOn(), variants); }
            catch (Exception ignored) {}
            if (variants.isEmpty()) variants.add(new ItemStack(item));
            for (ItemStack stack : variants) {
                String id = key.toString() + (stack.getItemDamage() == 0 ? "" : "#" + stack.getItemDamage());
                String unique = id + "|" + cleanName(stack.getDisplayName());
                if (seen.add(unique)) all.add(new Entry(id, cleanName(stack.getDisplayName()), stack, -1));
            }
        }
        for (Object idObject : EntityList.entityEggs.keySet()) {
            if (!(idObject instanceof Integer)) continue;
            int entityId = ((Integer) idObject).intValue();
            String id = EntityList.getStringFromID(entityId);
            if (id == null) continue;
            ItemStack egg = new ItemStack(Items.spawn_egg, 1, entityId);
            String resourceId = "minecraft:" + id.toLowerCase(Locale.ROOT);
            if (seen.add(resourceId)) all.add(new Entry(resourceId, cleanName(egg.getDisplayName()), egg, entityId));
        }
        Collections.sort(all, new Comparator<Entry>() {
            @Override public int compare(Entry left, Entry right) { return left.name.compareToIgnoreCase(right.name); }
        });
    }

    private static String cleanName(String value) { return value == null ? "Unbekannt" : value.replaceAll("\\u00a7.", ""); }

    private void filter() {
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (Entry entry : all) {
            if (query.isEmpty() || entry.name.toLowerCase(Locale.ROOT).contains(query) || entry.id.toLowerCase(Locale.ROOT).contains(query)) {
                filtered.add(entry);
            }
        }
        scroll = Math.min(scroll, Math.max(0, filtered.size() - VISIBLE_ROWS));
        selected = -1;
        lastSearch = search.getText();
    }

    @Override public void updateScreen() {
        search.updateCursorCounter();
        if (!lastSearch.equals(search.getText())) filter();
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0) mc.displayGuiScreen(parent);
        else if (button.id == 1 && selected >= 0 && selected < filtered.size()) {
            Entry entry = filtered.get(selected);
            parent.select(entry.id, entry.name);
            mc.displayGuiScreen(parent);
        }
    }

    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
        else if (keyCode == Keyboard.KEY_UP) scroll = Math.max(0, scroll - 1);
        else if (keyCode == Keyboard.KEY_DOWN) scroll = Math.min(Math.max(0, filtered.size() - VISIBLE_ROWS), scroll + 1);
        else search.textboxKeyTyped(typedChar, keyCode);
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        super.mouseClicked(mouseX, mouseY, button);
        search.mouseClicked(mouseX, mouseY, button);
        int top = 76;
        int left = centerX(300);
        if (mouseX >= left && mouseX <= left + 300 && mouseY >= top && mouseY < top + VISIBLE_ROWS * ROW_HEIGHT) {
            int index = scroll + (mouseY - top) / ROW_HEIGHT;
            if (index < filtered.size()) selected = index;
        }
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll += wheel < 0 ? 1 : -1;
            scroll = Math.max(0, Math.min(Math.max(0, filtered.size() - VISIBLE_ROWS), scroll));
        }
    }

    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawTradecoreBackground("Block oder Mob auswählen");
        search.drawTextBox();
        int top = 76, left = centerX(300);

        // Erst alle Zeilenhintergründe, danach Icons und Text rendern. So kann keine
        // halbtransparente Fläche über den Item-Texturen liegen.
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= filtered.size()) break;
            int y = top + row * ROW_HEIGHT;
            drawRect(left, y, left + 300, y + ROW_HEIGHT - 2, index == selected ? 0xAA805500 : 0x88303840);
        }

        GlStateManager.pushMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableDepth();
        GlStateManager.enableRescaleNormal();
        RenderHelper.enableGUIStandardItemLighting();
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= filtered.size()) break;
            Entry entry = filtered.get(index);
            int y = top + row * ROW_HEIGHT;
            itemRender.renderItemAndEffectIntoGUI(entry.stack, left + 3, y + 3);
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= filtered.size()) break;
            Entry entry = filtered.get(index);
            int y = top + row * ROW_HEIGHT;
            drawString(fontRendererObj, entry.name, left + 25, y + 8, 0xFFFFFFFF);
        }
        if (selected >= 0 && selected < filtered.size()) drawPreview(filtered.get(selected), mouseX, mouseY);
        drawString(fontRendererObj, (filtered.isEmpty() ? 0 : scroll + 1) + "-" + Math.min(filtered.size(), scroll + VISIBLE_ROWS)
            + " / " + filtered.size(), left, height - 57, 0xFFAAAAAA);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawPreview(Entry entry, int mouseX, int mouseY) {
        if (entry.entityId < 0 || mc.theWorld == null) return;
        try {
            Entity entity = EntityList.createEntityByID(entry.entityId, mc.theWorld);
            if (entity instanceof EntityLivingBase) {
                GuiInventory.drawEntityOnScreen(width / 2 + 180, height - 54, 28,
                    width / 2 + 180 - mouseX, height - 80 - mouseY, (EntityLivingBase) entity);
            }
        } catch (Exception ignored) {}
    }

    private static final class Entry {
        final String id;
        final String name;
        final ItemStack stack;
        final int entityId;
        Entry(String id, String name, ItemStack stack, int entityId) {
            this.id = id; this.name = name; this.stack = stack; this.entityId = entityId;
        }
    }
}
