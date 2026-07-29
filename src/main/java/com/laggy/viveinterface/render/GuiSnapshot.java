package com.laggy.viveinterface.render;

import com.laggy.viveinterface.debug.DebugLog;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;

/**
 * A per-frame copy of Vivecraft's HUD framebuffer into our own texture.
 *
 * <p>Why the copy exists: placed panels sample the HUD live, but the masking step (see {@link HudMask})
 * punches holes into the SHARED {@link GuiHandler#GUI_FRAMEBUFFER} so the cut region vanishes from
 * Vivecraft's flat panel. If the panels sampled that same framebuffer they'd lose their content too.
 * So we snapshot the full HUD here first; panels render from the snapshot, the flat panel gets the holes.
 *
 * <p>{@link #capture()} must run while {@code GUI_FRAMEBUFFER} is the bound (read) framebuffer — i.e.
 * from the HUD render callback — so {@code glCopyTexSubImage2D} reads the freshly-composited HUD.
 */
public final class GuiSnapshot {

    private static int texId = 0;
    private static int w = 0, h = 0;

    private GuiSnapshot() {}

    public static boolean ready() { return texId != 0; }
    public static int texId() { return texId; }
    public static int width() { return w; }
    public static int height() { return h; }

    /** Copy the currently-bound HUD framebuffer's colour into our texture. */
    public static void capture() {
        RenderTarget fb = GuiHandler.GUI_FRAMEBUFFER;
        if (fb == null) return;
        int fw = fb.width, fh = fb.height;
        if (fw <= 0 || fh <= 0) return;
        try {
            ensure(fw, fh);
            GlStateManager._bindTexture(texId);
            // Reads from GL_READ_FRAMEBUFFER, which is GUI_FRAMEBUFFER while the HUD is drawn.
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, fw, fh);
            DebugLog.once("snapshot", "SNAP", "first HUD capture " + fw + "x" + fh + " texId=" + texId);
        } catch (Throwable t) {
            DebugLog.error("SNAP", "capture failed (fb=" + fw + "x" + fh + ")", t);
        }
    }

    private static void ensure(int fw, int fh) {
        if (texId != 0 && fw == w && fh == h) return;
        if (texId == 0) texId = GlStateManager._genTexture();
        GlStateManager._bindTexture(texId);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, fw, fh, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.IntBuffer) null);
        w = fw; h = fh;
    }
}
