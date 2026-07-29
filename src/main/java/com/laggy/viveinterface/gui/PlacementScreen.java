package com.laggy.viveinterface.gui;

import com.laggy.viveinterface.panel.Panel;
import com.laggy.viveinterface.panel.PanelAnchor;
import com.laggy.viveinterface.panel.PanelManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Placement mode — the state you land in after pressing <b>Done</b> on the {@link CutScreen}.
 *
 * <p>It exists to solve a VR input problem: outside a screen, squeezing a trigger also makes Vivecraft
 * teleport/walk. Because this is a real {@link Screen}, Minecraft suppresses movement and world
 * interaction for free, while Vivecraft still tracks your hands — so you can grab cut pieces and place
 * them on your body without moving. Only in this mode do pieces tint green and stick to a body part on
 * release.
 *
 * <p>Deliberately almost fully transparent: in VR this renders as the flat panel, so anything we don't
 * draw stays see-through and you can watch your hands and the pieces in the world. Pressing Done just
 * leaves every piece exactly where it is.
 */
public class PlacementScreen extends Screen {

    public PlacementScreen() {
        super(Component.literal("ViveInterface — Place pieces"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 - 40, this.height - 30, 80, 20).build());
    }

    /** No dirt/blur backdrop — keep the VR panel see-through so you can see your hands and pieces. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // intentionally empty
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = this.width / 2;

        // A small backing just behind the text, so it stays readable over the world.
        int boxW = 300, boxH = 86;
        int bx = cx - boxW / 2, by = 10;
        g.fill(bx, by, bx + boxW, by + boxH, 0xB0101018);
        g.renderOutline(bx, by, boxW, boxH, 0xFF55FF88);

        g.drawCenteredString(this.font, Component.literal("§fPlacement mode §7— movement is locked"),
                cx, by + 6, 0xFFFFFF);
        g.drawCenteredString(this.font,
                Component.literal("§7Reach a hand into a piece (it turns §agreen§7)"), cx, by + 22, 0xCCCCCC);
        g.drawCenteredString(this.font,
                Component.literal("§7Squeeze that hand's §ftrigger§7 to grab and move it"), cx, by + 34, 0xCCCCCC);
        g.drawCenteredString(this.font,
                Component.literal("§7Let go on your §fother hand or head§7 to stick it there"), cx, by + 46, 0xCCCCCC);
        g.drawCenteredString(this.font,
                Component.literal("§7Let go anywhere else and it stays in the world"), cx, by + 58, 0xCCCCCC);
        g.drawCenteredString(this.font,
                Component.literal("§8Press Done to finish — pieces stay where they are"), cx, by + 70, 0x888888);

        // Where each piece currently lives, so you can tell what stuck and what didn't.
        List<Panel> all = PanelManager.all();
        int y = by + boxH + 6;
        if (all.isEmpty()) {
            g.drawCenteredString(this.font, Component.literal("§8no pieces cut yet"), cx, y, 0x777777);
        } else {
            for (int i = 0; i < all.size() && i < 8; i++) {
                PanelAnchor a = all.get(i).anchor;
                String where = (a == PanelAnchor.WORLD) ? "§7in the world" : "§aon your " + label(a);
                g.drawCenteredString(this.font,
                        Component.literal("§f#" + (i + 1) + " §8— " + where), cx, y, 0xAAAAAA);
                y += 11;
            }
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private static String label(PanelAnchor a) {
        return switch (a) {
            case MAIN_HAND -> "main hand";
            case OFF_HAND -> "off hand";
            case HEAD -> "head";
            default -> "body";
        };
    }

    @Override
    public boolean isPauseScreen() { return false; }   // keep VR + world ticking behind the panel
}
