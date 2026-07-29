package com.laggy.viveinterface.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;

/**
 * Access to Vivecraft's composited desktop-HUD framebuffer.
 *
 * <p>{@link GuiHandler#GUI_FRAMEBUFFER} is a {@code public static} {@link RenderTarget} into which
 * Vivecraft renders the ENTIRE flat 2D HUD every frame — vanilla hotbar/chat AND any mod that draws
 * to the HUD (Xaero's Minimap, JourneyMap, Cobblemon overlays, ...). We never touch those mods; we
 * just sample sub-rectangles of this one texture. Because the framebuffer re-renders each frame, a
 * minimap cut onto your wrist stays live for free.
 */
public final class GuiTexture {

    private GuiTexture() {}

    public static boolean available() {
        return GuiHandler.GUI_FRAMEBUFFER != null;
    }

    /** OpenGL color-attachment texture id of the HUD framebuffer, or 0 if not ready. */
    public static int colorTexId() {
        RenderTarget fb = GuiHandler.GUI_FRAMEBUFFER;
        return fb == null ? 0 : fb.getColorTextureId();
    }

    public static int width() {
        RenderTarget fb = GuiHandler.GUI_FRAMEBUFFER;
        return fb == null ? 0 : fb.width;
    }

    public static int height() {
        RenderTarget fb = GuiHandler.GUI_FRAMEBUFFER;
        return fb == null ? 0 : fb.height;
    }
}
