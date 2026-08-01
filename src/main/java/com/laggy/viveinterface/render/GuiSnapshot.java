package com.laggy.viveinterface.render;

import com.laggy.viveinterface.debug.DebugLog;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;

/**
 * A per-frame copy of Vivecraft's HUD framebuffer into our own GPU texture.
 *
 * <p>Why the copy exists: placed panels sample the HUD live, but the masking step (see {@link HudMask})
 * punches holes into the SHARED {@link GuiHandler#GUI_FRAMEBUFFER} so the cut region vanishes from
 * Vivecraft's flat panel. If the panels sampled that same framebuffer they'd lose their content too.
 * So we snapshot the full HUD here first; panels render from the snapshot, the flat panel gets the holes.
 *
 * <p><b>26.2 note:</b> this used to be raw OpenGL ({@code glCopyTexSubImage2D} plus manual framebuffer
 * binding). 26.x hides GL behind Blaze3D's GPU abstraction — and with the Vulkan backend there may be
 * no GL at all — so the copy is now a {@code CommandEncoder.copyTextureToTexture} between the
 * framebuffer's colour texture and ours.
 */
public final class GuiSnapshot {

    private static GpuTexture texture;
    private static GpuTextureView view;
    private static int w, h;

    private GuiSnapshot() {}

    public static boolean ready() { return view != null; }
    /** The raw texture, for wrapping into a registered {@link SnapshotTexture}. */
    public static GpuTexture texture() { return texture; }
    /** The snapshot as something {@code GuiGraphicsExtractor.blit} and our render pipeline can sample. */
    public static GpuTextureView view() { return view; }
    public static int width() { return w; }
    public static int height() { return h; }

    /** Copy Vivecraft's HUD framebuffer into our texture (VR path). */
    public static void capture() {
        capture(GuiHandler.GUI_FRAMEBUFFER);
    }

    /**
     * Copy a framebuffer's colour into our texture. In VR that's Vivecraft's HUD buffer; on desktop
     * (no VR) the caller passes the main render target so the mod is still testable with a mouse.
     */
    public static void capture(RenderTarget fb) {
        if (fb == null) return;
        int fw = fb.width, fh = fb.height;
        if (fw <= 0 || fh <= 0) return;
        GpuTexture src = fb.getColorTexture();
        if (src == null) return;
        try {
            ensure(fw, fh);
            RenderSystem.getDevice().createCommandEncoder()
                    .copyTextureToTexture(src, texture, 0, 0, 0, 0, 0, fw, fh);
            DebugLog.once("snapshot", "SNAP", "first HUD capture " + fw + "x" + fh);
        } catch (Throwable t) {
            DebugLog.error("SNAP", "capture failed (fb=" + fw + "x" + fh + ")", t);
        }
    }

    private static void ensure(int fw, int fh) {
        if (texture != null && fw == w && fh == h) return;
        close();
        GpuDevice device = RenderSystem.getDevice();
        texture = device.createTexture("viveinterface_hud_snapshot",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                GpuFormat.RGBA8_UNORM, fw, fh, 1, 1);
        view = device.createTextureView(texture);
        w = fw; h = fh;
    }

    /** Free the GPU texture (on resize, or when the framebuffer goes away). */
    public static void close() {
        if (view != null) { view.close(); view = null; }
        if (texture != null) { texture.close(); texture = null; }
        w = h = 0;
    }
}
