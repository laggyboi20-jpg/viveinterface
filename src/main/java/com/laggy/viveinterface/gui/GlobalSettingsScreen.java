package com.laggy.viveinterface.gui;

import com.laggy.viveinterface.config.ViveConfig;
import com.laggy.viveinterface.panel.Placement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Global ViveInterface settings (as opposed to the per-piece {@link ViveInterfaceScreen}): debug
 * logging, trigger mapping, real-vs-quad models, and the cutting geometry tunables. Every change
 * saves immediately. Reached from Mod Menu's config cog, or the pieces screen's "Settings" button.
 */
public class GlobalSettingsScreen extends Screen {

    private static final String[] LABELS =
            {"Grab radius", "Glue radius", "Blade length", "Stick length", "Menu distance", "Menu width"};
    private static final float[] STEPS = {0.01f, 0.01f, 0.02f, 0.02f, 0.05f, 0.05f};

    private final Screen parent;

    public GlobalSettingsScreen(Screen parent) {
        super(Component.literal("ViveInterface — Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 30;

        addRenderableWidget(Button.builder(bool("Debug logging", ViveConfig.get().debugLogging),
                b -> { ViveConfig.toggleDebug(); rebuild(); }).bounds(cx - 150, y, 300, 20).build());
        y += 22;
        addRenderableWidget(Button.builder(bool("Swap cut/release triggers", ViveConfig.get().swapTriggers),
                b -> { ViveConfig.get().swapTriggers = !ViveConfig.get().swapTriggers; saved(); })
                .bounds(cx - 150, y, 300, 20).build());
        y += 22;
        addRenderableWidget(Button.builder(bool("Real item models (sword/stick)", ViveConfig.get().realModels),
                b -> { ViveConfig.get().realModels = !ViveConfig.get().realModels; saved(); })
                .bounds(cx - 150, y, 300, 20).build());
        y += 26;

        for (int i = 0; i < LABELS.length; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(Component.literal("−"),
                    b -> { addF(idx, -STEPS[idx]); saved(); }).bounds(cx - 150, y, 24, 20).build());
            addRenderableWidget(Button.builder(Component.literal("+"),
                    b -> { addF(idx, STEPS[idx]); saved(); }).bounds(cx + 126, y, 24, 20).build());
            y += 22;
        }
        y += 6;

        // Per-element transform editors (XYZ + rotation + scale).
        ViveConfig c = ViveConfig.get();
        addRenderableWidget(Button.builder(Component.literal("Sword transform…"),
                b -> minecraft.setScreen(new PlacementEditScreen(this, "Sword transform", c.swordPlace)))
                .bounds(cx - 150, y, 145, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Stick transform…"),
                b -> minecraft.setScreen(new PlacementEditScreen(this, "Stick transform", c.stickPlace)))
                .bounds(cx + 5, y, 145, 20).build());
        y += 22;
        addRenderableWidget(Button.builder(Component.literal("Hand panel default…"),
                b -> minecraft.setScreen(new PlacementEditScreen(this, "Hand panel default", c.handPanelPlace)))
                .bounds(cx - 150, y, 145, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Head panel default…"),
                b -> minecraft.setScreen(new PlacementEditScreen(this, "Head panel default", c.headPanelPlace)))
                .bounds(cx + 5, y, 145, 20).build());
        y += 22;
        addRenderableWidget(Button.builder(Component.literal("Held piece default…"),
                b -> minecraft.setScreen(new PlacementEditScreen(this, "Held piece default", c.heldPanelPlace)))
                .bounds(cx - 150, y, 145, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Edit placed pieces…"),
                b -> minecraft.setScreen(new ViveInterfaceScreen(this)))
                .bounds(cx + 5, y, 145, 20).build());
        y += 26;
        addRenderableWidget(Button.builder(Component.literal("Reset tunables"),
                b -> { resetDefaults(); saved(); }).bounds(cx - 150, y, 145, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(cx + 5, y, 145, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        int y = 30 + 3 * 22 + 26;   // first float row baseline
        for (int i = 0; i < LABELS.length; i++) {
            g.drawCenteredString(this.font,
                    Component.literal(String.format("%s: %.2f", LABELS[i], getF(i))),
                    this.width / 2, y + 6, 0xCCCCCC);
            y += 22;
        }
    }

    private static Component bool(String label, boolean v) {
        return Component.literal(label + ": " + (v ? "ON" : "OFF"));
    }

    private static float getF(int i) {
        ViveConfig c = ViveConfig.get();
        return switch (i) {
            case 0 -> c.grabRadius;
            case 1 -> c.glueRadius;
            case 2 -> c.bladeLength;
            case 3 -> c.selectStickLength;
            case 4 -> c.paperDistance;
            default -> c.paperWidth;
        };
    }

    private static void addF(int i, float d) {
        ViveConfig c = ViveConfig.get();
        float v = Math.max(0.01f, getF(i) + d);
        switch (i) {
            case 0 -> c.grabRadius = v;
            case 1 -> c.glueRadius = v;
            case 2 -> c.bladeLength = v;
            case 3 -> c.selectStickLength = v;
            case 4 -> c.paperDistance = v;
            default -> c.paperWidth = v;
        }
    }

    private static void resetDefaults() {
        ViveConfig c = ViveConfig.get();
        c.swapTriggers = false;
        c.realModels = true;
        c.bladeLength = 0.42f;
        c.selectStickLength = 0.16f;
        c.grabRadius = 0.07f;
        c.glueRadius = 0.18f;
        c.paperDistance = 0.45f;
        c.paperWidth = 0.55f;
        c.swordPlace = new Placement(0f, 0f, 0f, 0f, -90f, 0f, 0.67f);
        c.stickPlace = new Placement(0f, 0f, 0f, 0f, -90f, 0f, 0.26f);
        c.handPanelPlace = Placement.onHand();
        c.headPanelPlace = Placement.onHead();
        c.heldPanelPlace = Placement.held();
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
