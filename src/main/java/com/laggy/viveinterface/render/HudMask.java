package com.laggy.viveinterface.render;

import com.laggy.viveinterface.debug.DebugLog;
import com.laggy.viveinterface.panel.Panel;
import com.laggy.viveinterface.panel.PanelManager;
import com.laggy.viveinterface.vr.VrPoses;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Runs at the end of HUD rendering (while {@code GUI_FRAMEBUFFER} is bound): first snapshots the full
 * HUD for the world panels, then erases each placed panel's region from the flat HUD so a cut piece
 * doesn't show in both places. "Erase" = write alpha 0 in that rectangle, leaving a see-through hole
 * in Vivecraft's flat panel while the world panels keep the full content from the snapshot.
 */
public final class HudMask {

    private HudMask() {}

    public static void register() {
        HudRenderCallback.EVENT.register(HudMask::onHud);
    }

    private static void onHud(GuiGraphics g, DeltaTracker tickDelta) {
        if (!VrPoses.vrActive() || !GuiTexture.available()) return;
        try {
            // 1) Full copy for the world panels (must happen before we punch holes below).
            GuiSnapshot.capture();

            // 2) Punch transparent holes for each placed panel.
            if (PanelManager.all().isEmpty()) return;
            int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();

            RenderSystem.disableBlend();
            RenderSystem.colorMask(false, false, false, true); // write only the alpha channel
            for (Panel p : PanelManager.all()) {
                int x0 = Math.round(Math.min(p.u0, p.u1) * sw);
                int x1 = Math.round(Math.max(p.u0, p.u1) * sw);
                int y0 = Math.round(Math.min(p.v0, p.v1) * sh);
                int y1 = Math.round(Math.max(p.v0, p.v1) * sh);
                g.fill(x0, y0, x1, y1, 0x00000000);            // alpha byte = 0 → transparent
            }
            g.flush();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.enableBlend();
            DebugLog.throttled("mask", 2000L, "MASK", "punched %d hole(s) at %dx%d",
                    PanelManager.all().size(), sw, sh);
        } catch (Throwable t) {
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.enableBlend();
            DebugLog.error("MASK", "hole-punch failed", t);
        }
    }
}
