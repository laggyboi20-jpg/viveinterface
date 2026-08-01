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

    /** The HUD framebuffer's colour texture view, or null if not ready. 26.2 has no raw GL texture id. */
    public static com.mojang.blaze3d.textures.GpuTextureView colorView() {
        RenderTarget fb = GuiHandler.GUI_FRAMEBUFFER;
        return fb == null ? null : fb.getColorTextureView();
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
