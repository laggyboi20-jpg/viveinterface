package com.laggy.viveinterface.render;

import com.laggy.viveinterface.ViveKeys;
import com.laggy.viveinterface.cut.PlacementMode;
import com.laggy.viveinterface.panel.Panel;
import com.laggy.viveinterface.panel.PanelAnchor;
import com.laggy.viveinterface.panel.PanelManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The on-screen instructions shown while {@link PlacementMode} is active. Drawn straight onto the HUD
 * (not as a screen) because placement mode has to stay in the world for the VR triggers and hands to
 * work — see {@link PlacementMode}.
 *
 * <p>Registered <b>after</b> {@link HudMask}, so this text is drawn after the HUD snapshot is taken and
 * therefore never ends up baked into a cut piece.
 */
public final class PlacementHud {

    private PlacementHud() {}

    public static void register() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("viveinterface", "placement_hud"), PlacementHud::onHud);
    }

    private static void onHud(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
        if (!PlacementMode.active()) return;
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int cx = w / 2;

        int boxW = 340, boxH = 84, bx = cx - boxW / 2, by = 6;
        g.fill(bx, by, bx + boxW, by + boxH, 0xB0101018);
        g.outline(bx, by, boxW, boxH, 0xFF55FF88);

        g.centeredText(mc.font,
                Component.literal("§aPlacement mode §7— movement locked"), cx, by + 5, 0xFFFFFF);
        g.centeredText(mc.font,
                Component.literal("§7Reach a hand into a piece (it turns §agreen§7)"), cx, by + 19, 0xCCCCCC);
        g.centeredText(mc.font,
                Component.literal("§7Hold the §fright trigger§7 to grab and move it"), cx, by + 31, 0xCCCCCC);
        g.centeredText(mc.font,
                Component.literal("§7Let go on a §fcyan hand§7 or the §eyellow head§7 box to stick it"), cx, by + 43, 0xCCCCCC);
        boolean offBound = ViveKeys.isBound(ViveKeys.grabOffHand);
        g.centeredText(mc.font, Component.literal(offBound
                        ? "§7Off hand grabs with your bound §fgrab-off-hand§7 button"
                        : "§8Off hand: bind \"Grab with off hand\" in Controls to a left button"),
                cx, by + 55, offBound ? 0xCCCCCC : 0x888888);
        g.centeredText(mc.font,
                Component.literal("§eTouch the green DONE box below you + squeeze to finish"), cx, by + 67, 0xFFFF88);

        // Where each piece currently lives, so you can tell what stuck and what didn't.
        List<Panel> all = PanelManager.all();
        int y = by + boxH + 4;
        for (int i = 0; i < all.size() && i < 8; i++) {
            PanelAnchor a = all.get(i).anchor;
            String where = (a == PanelAnchor.WORLD) ? "§7in the world" : "§aon your " + label(a);
            g.centeredText(mc.font, Component.literal("§f#" + (i + 1) + " §8— " + where), cx, y, 0xAAAAAA);
            y += 11;
        }
        if (all.isEmpty()) {
            g.centeredText(mc.font, Component.literal("§8no pieces cut yet — press N, cut one first"),
                    cx, y, 0x777777);
        }
    }

    private static String label(PanelAnchor a) {
        return switch (a) {
            case MAIN_HAND -> "main hand";
            case OFF_HAND -> "off hand";
            case HEAD -> "head";
            default -> "body";
        };
    }
}
