package de.tradecore.legacy.gui;

import de.tradecore.legacy.model.BdtData;
import de.tradecore.legacy.service.ActivityService;
import de.tradecore.legacy.service.BdtService;
import de.tradecore.legacy.util.BdtPreview;
import de.tradecore.legacy.util.Browser;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

public final class SettingsScreen extends BaseScreen {
    private final GuiScreen parent;
    private GuiButton activity;
    public SettingsScreen() { this(null); }
    public SettingsScreen(GuiScreen parent) { this.parent = parent; }

    @Override public void initGui() {
        buttonList.clear();
        int x = centerX(220), y = 62;
        buttonList.add(new GuiButton(1, x, y, 220, 20, "Block des Tages öffnen"));
        buttonList.add(new GuiButton(2, x, y + 22, 220, 20, "BdT-Daten aktualisieren"));
        buttonList.add(new GuiButton(3, x, y + 44, 220, 20, "BdT-Highlight konfigurieren"));
        buttonList.add(new GuiButton(4, x, y + 66, 220, 20, "Funktionen"));
        buttonList.add(new GuiButton(5, x, y + 88, 220, 20, "Discord beitreten"));
        activity = new GuiButton(6, x, y + 110, 220, 20,
            "Aktivitätsstatus: " + EnumChatFormatting.YELLOW + "Wird geprüft ...");
        activity.enabled = false;
        buttonList.add(activity);
        buttonList.add(new GuiButton(0, x + 60, y + 142, 100, 20, "Fertig"));
        refreshActivity();
    }

    private String activityText() {
        return "Aktivitätsstatus: " + (ActivityService.isActive() ? EnumChatFormatting.GREEN + "Aktiv" : EnumChatFormatting.RED + "Inaktiv");
    }

    @Override protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0: mc.displayGuiScreen(parent); break;
            case 1: openBdt(false); break;
            case 2: openBdt(true); break;
            case 3: mc.displayGuiScreen(new HighlightSettingsScreen(this)); break;
            case 4: mc.displayGuiScreen(new FeatureSettingsScreen(this)); break;
            case 5: Browser.open(Browser.DISCORD_URL); break;
            case 6: refreshActivity(); break;
            default: break;
        }
    }

    private void refreshActivity() {
        if (activity == null) return;
        if (mc.thePlayer == null) {
            activity.enabled = false;
            activity.displayString = "Aktivitätsstatus: " + EnumChatFormatting.RED + "Nicht verfügbar";
            return;
        }
        activity.enabled = false;
        activity.displayString = "Aktivitätsstatus: " + EnumChatFormatting.YELLOW + "Wird geprüft ...";
        ActivityService.refresh(mc.thePlayer.getUniqueID().toString(), new Runnable() {
            @Override public void run() {
                if (activity != null) {
                    activity.enabled = true;
                    activity.displayString = activityText();
                }
            }
        });
    }

    private void openBdt(boolean force) {
        BdtService.fetch(force, new Runnable() {
            @Override public void run() { mc.displayGuiScreen(new BdtScreen(SettingsScreen.this)); }
        });
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawTradecoreBackground("Tradecore Einstellungen");
        drawBdtPreview();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawBdtPreview() {
        BdtData data = BdtService.getCurrent();
        if (data == null) {
            drawCenteredString(fontRendererObj, "Aktueller BdT: nicht geladen", width / 2, 44, 0xFF888888);
            return;
        }
        String name = data.item_name == null || data.item_name.trim().isEmpty() ? data.item_id : data.item_name;
        String label = "BdT: " + name;
        ItemStack stack = BdtPreview.createStack(data.item_id);
        int iconWidth = stack == null ? 0 : 22;
        int rowWidth = iconWidth + fontRendererObj.getStringWidth(label);
        int left = (width - rowWidth) / 2;
        if (stack != null) drawItemIcon(stack, left, 37);
        drawString(fontRendererObj, label, left + iconWidth, 43, 0xFF55FFFF);
    }
}
