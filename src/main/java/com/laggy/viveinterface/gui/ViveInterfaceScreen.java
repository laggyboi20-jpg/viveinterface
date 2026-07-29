package com.laggy.viveinterface.gui;

import com.laggy.viveinterface.panel.Panel;
import com.laggy.viveinterface.panel.PanelAnchor;
import com.laggy.viveinterface.panel.PanelManager;
import com.laggy.viveinterface.panel.PanelStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The "pieces" settings tab: step through each saved panel and tweak its scale, position and (for
 * hand/head-anchored pieces) rotation, switch its anchor, or delete it. Every change saves to disk.
 * Position is the WORLD nudge (0,0,0 = where left) or the hand/head offset, depending on anchor.
 */
public class ViveInterfaceScreen extends Screen {

    private static final float SCALE_STEP = 0.1f;
    private static final float POS_STEP = 0.05f;
    private static final float ROT_STEP = 5f;

    private int index = 0;
    private final Screen parent;

    public ViveInterfaceScreen() {
        this(null);
    }

    public ViveInterfaceScreen(Screen parent) {
        super(Component.literal("ViveInterface — Pieces"));
        this.parent = parent;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
        else super.onClose();
    }

    private List<Panel> panels() { return PanelManager.all(); }

    private Panel current() {
        List<Panel> ps = panels();
        if (ps.isEmpty()) return null;
        index = Math.floorMod(index, ps.size());
        return ps.get(index);
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 54;
        Panel p = current();

        // Link to the global settings (debug toggle, tunables, models) — top-left, always visible.
        addRenderableWidget(Button.builder(Component.literal("⚙ Settings"),
                b -> minecraft.setScreen(new GlobalSettingsScreen(this)))
                .bounds(10, 10, 110, 20).build());

        addRenderableWidget(Button.builder(Component.literal("< Prev"), b -> { index--; rebuild(); })
                .bounds(cx - 160, y, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Next >"), b -> { index++; rebuild(); })
                .bounds(cx + 90, y, 70, 20).build());

        if (p == null) return;
        y += 38;

        addRenderableWidget(Button.builder(Component.literal("Scale -"), b -> {
            p.scale = Math.max(0.1f, p.scale * (1f - SCALE_STEP)); save();
        }).bounds(cx - 160, y, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Scale +"), b -> {
            p.scale *= (1f + SCALE_STEP); save();
        }).bounds(cx + 90, y, 70, 20).build());
        y += 26;

        addPosRow(cx, y, "X", 0); y += 22;
        addPosRow(cx, y, "Y", 1); y += 22;
        addPosRow(cx, y, "Z", 2); y += 26;

        if (p.isHandAnchored()) {
            addRotRow(cx, y, "Yaw", 0); y += 22;
            addRotRow(cx, y, "Pitch", 1); y += 22;
            addRotRow(cx, y, "Roll", 2); y += 26;
        }

        addRenderableWidget(Button.builder(Component.literal("Anchor: " + p.anchor), b -> {
            cycleAnchor(p); save();
        }).bounds(cx - 100, y, 200, 20).build());
        y += 26;

        addRenderableWidget(Button.builder(Component.literal("Delete piece"), b -> {
            PanelManager.remove(p); PanelStore.save(); index--; rebuild();
        }).bounds(cx - 100, y, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(cx + 5, y, 95, 20).build());
    }

    private void addPosRow(int cx, int y, String label, int axis) {
        Panel p = current();
        addRenderableWidget(Button.builder(Component.literal(label + " -"), b -> {
            nudgePos(p, axis, -POS_STEP); save();
        }).bounds(cx - 160, y, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal(label + " +"), b -> {
            nudgePos(p, axis, POS_STEP); save();
        }).bounds(cx + 90, y, 70, 20).build());
    }

    private void addRotRow(int cx, int y, String label, int axis) {
        Panel p = current();
        addRenderableWidget(Button.builder(Component.literal(label + " -"), b -> {
            nudgeRot(p, axis, -ROT_STEP); save();
        }).bounds(cx - 160, y, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal(label + " +"), b -> {
            nudgeRot(p, axis, ROT_STEP); save();
        }).bounds(cx + 90, y, 70, 20).build());
    }

    /** Position edits the WORLD nudge, or the hand/head offset when body-anchored. */
    private static void nudgePos(Panel p, int axis, float d) {
        if (p.isHandAnchored()) {
            switch (axis) {
                case 0 -> p.place.posX += d;
                case 1 -> p.place.posY += d;
                default -> p.place.posZ += d;
            }
        } else {
            switch (axis) {
                case 0 -> p.userOffset.x += d;
                case 1 -> p.userOffset.y += d;
                default -> p.userOffset.z += d;
            }
        }
    }

    private static void nudgeRot(Panel p, int axis, float d) {
        switch (axis) {
            case 0 -> p.place.yaw += d;
            case 1 -> p.place.pitch += d;
            default -> p.place.roll += d;
        }
    }

    /** Cycle anchor, snapping body anchors to their tuned default placement. */
    private static void cycleAnchor(Panel p) {
        PanelAnchor[] v = PanelAnchor.values();
        PanelAnchor nextA = v[(p.anchor.ordinal() + 1) % v.length];
        if (nextA == PanelAnchor.WORLD) {
            p.dropToWorld();
        } else {
            p.anchorToBody(nextA);
        }
    }

    private void save() { PanelStore.save(); rebuild(); }

    private void rebuild() { clearWidgets(); init(); }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        g.drawCenteredString(this.font, this.title, cx, 18, 0xFFFFFF);

        List<Panel> ps = panels();
        if (ps.isEmpty()) {
            g.drawCenteredString(this.font, Component.literal("No pieces yet — cut some in VR (N)."),
                    cx, 84, 0xAAAAAA);
            return;
        }
        Panel p = ps.get(Math.floorMod(index, ps.size()));
        g.drawCenteredString(this.font, Component.literal("Piece " + (index + 1) + " / " + ps.size()
                + "   [" + p.anchor + "]"), cx, 60, 0xFFFF80);
        String pos = p.isHandAnchored()
                ? String.format("off %.2f, %.2f, %.2f   rot %.0f/%.0f/%.0f",
                    p.place.posX, p.place.posY, p.place.posZ, p.place.yaw, p.place.pitch, p.place.roll)
                : String.format("nudge %.2f, %.2f, %.2f", p.userOffset.x, p.userOffset.y, p.userOffset.z);
        g.drawCenteredString(this.font, Component.literal(String.format("scale %.2f   %s", p.scale, pos)),
                cx, 118, 0xCCCCCC);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
