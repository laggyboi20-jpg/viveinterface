package com.laggy.viveinterface.gui;

import com.laggy.viveinterface.config.ViveConfig;
import com.laggy.viveinterface.panel.Placement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Generic editor for one {@link Placement}: XYZ offset, yaw/pitch/roll, and scale — each with −/+
 * buttons. Edits the object in place and saves {@link ViveConfig} on every change. Used for the sword,
 * the selection stick, and the default hand/held/head panel placements.
 */
public class PlacementEditScreen extends Screen {

    private static final String[] LABELS = {"X", "Y", "Z", "Yaw", "Pitch", "Roll", "Scale"};
    private static final float[] STEPS = {0.02f, 0.02f, 0.02f, 5f, 5f, 5f, 0.05f};

    private final Screen parent;
    private final Placement p;

    public PlacementEditScreen(Screen parent, String title, Placement placement) {
        super(Component.literal(title));
        this.parent = parent;
        this.p = placement;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 40;
        for (int i = 0; i < LABELS.length; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(Component.literal("−"),
                    b -> { add(idx, -STEPS[idx]); saved(); }).bounds(cx - 150, y, 24, 20).build());
            addRenderableWidget(Button.builder(Component.literal("+"),
                    b -> { add(idx, STEPS[idx]); saved(); }).bounds(cx + 126, y, 24, 20).build());
            y += 24;
        }
        y += 8;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cx - 50, y, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        g.drawCenteredString(this.font, this.title, cx, 16, 0xFFFFFF);
        int y = 40;
        for (int i = 0; i < LABELS.length; i++) {
            String unit = i < 3 ? "m" : (i < 6 ? "°" : "×");
            g.drawCenteredString(this.font,
                    Component.literal(String.format("%s: %.2f%s", LABELS[i], get(i), unit)),
                    cx, y + 6, 0xCCCCCC);
            y += 24;
        }
    }

    private float get(int i) {
        return switch (i) {
            case 0 -> p.posX;
            case 1 -> p.posY;
            case 2 -> p.posZ;
            case 3 -> p.yaw;
            case 4 -> p.pitch;
            case 5 -> p.roll;
            default -> p.scale;
        };
    }

    private void add(int i, float d) {
        switch (i) {
            case 0 -> p.posX += d;
            case 1 -> p.posY += d;
            case 2 -> p.posZ += d;
            case 3 -> p.yaw += d;
            case 4 -> p.pitch += d;
            case 5 -> p.roll += d;
            default -> p.scale = Math.max(0.05f, p.scale + d);
        }
    }

    private void saved() { ViveConfig.save(); rebuild(); }

    private void rebuild() { clearWidgets(); init(); }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
        else super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
