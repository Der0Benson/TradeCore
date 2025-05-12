package de.tradecore.tradecore;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class ConfirmScreen extends Screen {

    private final Consumer<Boolean> callback;
    private final Text message;

    public ConfirmScreen(Consumer<Boolean> callback, Text title, Text message) {
        super(title);
        this.callback = callback;
        this.message = message;
    }

    @Override
    protected void init() {
        super.init();
        if (this.client == null) return;

        int centerX = this.width / 2;
        int buttonWidth = 98;
        int buttonHeight = 20;
        int buttonSpacing = 5;
        int topY = this.height / 2 + 20;

        // Ja Button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Ja"), button -> {
            callback.accept(true);
        }).dimensions(centerX - buttonWidth - buttonSpacing, topY, buttonWidth, buttonHeight).build());

        // Nein Button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Nein"), button -> {
            callback.accept(false);
        }).dimensions(centerX + buttonSpacing, topY, buttonWidth, buttonHeight).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 40, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, this.message, this.width / 2, 90, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}