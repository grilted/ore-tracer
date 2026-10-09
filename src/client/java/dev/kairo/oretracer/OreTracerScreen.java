package dev.kairo.oretracer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class OreTracerScreen extends Screen {
    private final Screen parent;
    private final OreTracerConfig config;

    public OreTracerScreen(Screen parent, OreTracerConfig config) {
        super(Component.literal("Ore Tracer Settings"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        int buttonWidth = 150;
        int gap = 8;
        int left = this.width / 2 - buttonWidth - gap / 2;
        int right = this.width / 2 + gap / 2;
        int startY = 48;

        OreType[] values = OreType.values();
        int finalRow = (values.length + 1) / 2;
        int rowSpacing = Math.max(20, Math.min(24, (this.height - startY - 36) / finalRow));
        for (int i = 0; i < values.length; i++) {
            OreType type = values[i];
            int x = i % 2 == 0 ? left : right;
            int y = startY + (i / 2) * rowSpacing;
            addRenderableWidget(Button.builder(label(type), button -> {
                config.toggle(type);
                button.setMessage(label(type));
            }).bounds(x, y, buttonWidth, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(this.width / 2 - 75, startY + finalRow * rowSpacing + 8, 150, 20).build());
    }

    private Component label(OreType type) {
        String marker = config.isEnabled(type) ? "ON" : "OFF";
        return Component.literal(type.displayName() + ": " + marker);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 18, 0xFFFFFFFF);
        graphics.centeredText(this.font,
                "Choose which ores get a glow, outline, tracer, and label",
                this.width / 2, 31, 0xFFB8B8B8);
    }

    @Override
    public void onClose() {
        config.save();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }
}
