package com.laggy.viveinterface.render;

import com.laggy.viveinterface.debug.DebugLog;
import com.laggy.viveinterface.vr.VrPoses;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Runs at the end of HUD rendering and snapshots the full HUD, so the world panels have something to
 * sample that isn't affected by anything drawn afterwards.
 *
 * <p><b>26.2 note — the hole-punch is currently disabled.</b> On 1.21.4 this also erased each placed
 * panel's rectangle from {@code GUI_FRAMEBUFFER} (alpha 0 via
 * {@code RenderSystem.colorMask(false,false,false,true)}), so a cut region vanished from Vivecraft's
 * flat panel instead of being drawn twice. 26.2 removed ad-hoc colour masking — write masks are baked
 * into a {@link com.mojang.blaze3d.pipeline.RenderPipeline} now — so restoring it needs a custom
 * pipeline with an alpha-only write mask. Until then a cut region appears in both the flat panel and
 * its world panel: cosmetic, not broken.
 */
public final class HudMask {

    private HudMask() {}

    public static void register() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("viveinterface", "hud_mask"), HudMask::onHud);
    }

    private static void onHud(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
        // While a screen is open (including our own cut screen) the framebuffer holds that screen, not
        // the plain HUD. Capturing then would snapshot the cut screen itself, so leave the last clean
        // HUD still alone until the screen closes. 26.2 has no current-screen accessor on Minecraft;
        // the mouse is only grabbed when no screen is up, which is the same signal.
        if (!Minecraft.getInstance().mouseHandler.isMouseGrabbed()) return;

        try {
            if (VrPoses.vrActive()) {
                if (!GuiTexture.available()) return;
                GuiSnapshot.capture();                                       // Vivecraft's HUD buffer
            } else {
                // Desktop (no VR): snapshot the main render target instead so the cut screen still
                // shows something and the mod can be tested with a mouse. That buffer also contains
                // the world behind the HUD.
                GuiSnapshot.capture(Minecraft.getInstance().gameRenderer.mainRenderTarget());
            }
        } catch (Throwable t) {
            DebugLog.error("MASK", "HUD capture failed", t);
        }
    }
}
