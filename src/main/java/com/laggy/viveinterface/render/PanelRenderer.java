package com.laggy.viveinterface.render;

import com.laggy.viveinterface.debug.DebugLog;
import com.laggy.viveinterface.panel.PanelManager;

/**
 * World rendering of placed panels — <b>not yet implemented on 26.2.</b>
 *
 * <p>Everything else in the mod is ported: cutting, grabbing, sticking, persistence and the whole
 * flat UI all work. Only drawing the pieces into the world is missing, because 26.2 removed every
 * mechanism the 1.21.4 version used and offers no drop-in replacement:
 *
 * <ul>
 *   <li>Fabric API has <b>no world-render event</b> — {@code WorldRenderEvents}/{@code
 *       WorldRenderContext} are gone, so there is no hook to draw from.</li>
 *   <li><b>{@code RenderType} and {@code MultiBufferSource} no longer exist</b>, so there is no
 *       ready-made way to draw a textured quad in the world at all.</li>
 *   <li>{@code Tesselator}/{@code BufferUploader}/{@code CoreShaders} are gone; drawing means a
 *       {@code RenderPipeline} plus manually managed {@code GpuBuffer} vertex data.</li>
 * </ul>
 *
 * <p>The plan is a mixin into {@code LevelRenderer.render(...)} — whose 26.2 signature is
 * {@code (GraphicsResourceAllocator, DeltaTracker, boolean, CameraRenderState, Matrix4fc,
 * GpuBufferSlice, Vector4f, boolean)} — for the hook, plus a custom {@code RenderPipeline} for the
 * panel quads. {@link GuiSnapshot} already hands out a {@code GpuTextureView}, which is what that
 * pipeline will sample, so the texture side is done.
 *
 * <p>This stub keeps the mod loading and logging on 26.2 so the rest can be exercised in-game and
 * the diagnostic log can be collected while the renderer is built.
 */
public final class PanelRenderer {

    private PanelRenderer() {}

    public static void register() {
        DebugLog.log("RENDER", "world panel rendering is NOT implemented on 26.2 yet — "
                + "cutting, grabbing and the UI work, but placed pieces are invisible in the world. "
                + "Needs a LevelRenderer mixin + a custom RenderPipeline (see PanelRenderer javadoc).");
    }

    /** Placed-panel count, so the rest of the mod and the logs can still report state. */
    public static int placedCount() {
        return PanelManager.all().size();
    }
}
