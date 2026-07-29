package com.laggy.viveinterface.render;

import com.laggy.viveinterface.config.ViveConfig;
import com.laggy.viveinterface.cut.CutTool;
import com.laggy.viveinterface.panel.Panel;
import com.laggy.viveinterface.panel.PanelManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws every placed panel in the world as a textured quad that samples the live HUD snapshot. The
 * actual cutting UI is a flat screen now ({@link com.laggy.viveinterface.gui.CutScreen}); this class
 * only renders the pieces that have been placed.
 */
public final class PanelRenderer {

    private PanelRenderer() {}

    public static void register() {
        WorldRenderEvents.END.register(PanelRenderer::onRender);
    }

    private static void onRender(WorldRenderContext ctx) {
        // Panels sample the snapshot (full HUD), not the live framebuffer (which gets holes punched
        // into it by HudMask so cut regions vanish from Vivecraft's flat panel).
        // No vrActive() check: placed panels also draw on desktop so the mod can be tested there.
        if (!GuiSnapshot.ready()) return;
        int texId = GuiSnapshot.texId();
        if (texId == 0) return;

        Vec3 cam = ctx.camera().getPosition();

        // Build the world transform from ctx.positionMatrix() — the camera-rotation matrix Minecraft
        // itself renders the level with — and translate each panel by (worldPos - cameraPos). Do NOT
        // use ctx.matrixStack(): at WorldRenderEvents.END it doesn't carry the camera transform in
        // 1.21.4, which left panels pinned to view space so they slid along as you walked.
        Matrix4f base = new Matrix4f(ctx.positionMatrix());

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();          // panels are double-sided
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        int bg = ViveConfig.get().backgroundColor;
        float bgA = ((bg >>> 24) & 0xFF) / 255f;

        Panel touched = CutTool.get().touchedPanel();
        for (Panel p : PanelManager.all()) {
            // Solid backing so translucent parts of the HUD don't show the world through them.
            // Alpha 0 = "no background" → skip and let it stay see-through.
            if (bgA > 0f) {
                renderQuad(base, cam, p, -0.001f,
                        ((bg >> 16) & 0xFF) / 255f, ((bg >> 8) & 0xFF) / 255f, (bg & 0xFF) / 255f, bgA);
            }
            renderPanel(base, cam, p, texId);
            if (p == touched) renderQuad(base, cam, p, 0.002f, 0.2f, 1f, 0.3f, 0.30f);  // grabbable → green
        }

        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** {@code base * translate(panel - camera) * rotate(panel)} — the panel's world transform. */
    private static Matrix4f modelOf(Matrix4f base, Vec3 cam, Panel.Resolved r) {
        return new Matrix4f(base)
                .translate((float) (r.pos().x - cam.x),
                           (float) (r.pos().y - cam.y),
                           (float) (r.pos().z - cam.z))
                .rotate(r.rot());
    }

    private static void renderPanel(Matrix4f base, Vec3 cam, Panel panel, int texId) {
        Panel.Resolved r = panel.resolve();
        if (r == null) return;

        float hw = panel.effectiveWidth() * 0.5f;
        float hh = panel.effectiveHeight() * 0.5f;

        Matrix4f m = modelOf(base, cam, r);

        // Framebuffer textures are bottom-left origin, so flip V.
        float tu0 = panel.u0, tu1 = panel.u1;
        float tv0 = 1f - panel.v0, tv1 = 1f - panel.v1;

        RenderSystem.setShader(CoreShaders.POSITION_TEX);
        RenderSystem.setShaderTexture(0, texId);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder bb = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.addVertex(m, -hw,  hh, 0).setUv(tu0, tv0); // top-left
        bb.addVertex(m, -hw, -hh, 0).setUv(tu0, tv1); // bottom-left
        bb.addVertex(m,  hw, -hh, 0).setUv(tu1, tv1); // bottom-right
        bb.addVertex(m,  hw,  hh, 0).setUv(tu1, tv0); // top-right
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    /**
     * A flat coloured quad the size of the panel, offset along its normal by {@code z} — used both for
     * the opaque backing (behind, negative z) and the green grabbable wash (in front, positive z).
     */
    private static void renderQuad(Matrix4f base, Vec3 cam, Panel panel, float z,
                                   float r, float g, float b, float a) {
        Panel.Resolved res = panel.resolve();
        if (res == null) return;
        float hw = panel.effectiveWidth() * 0.5f, hh = panel.effectiveHeight() * 0.5f;

        Matrix4f m = modelOf(base, cam, res);
        RenderSystem.setShader(CoreShaders.POSITION_COLOR);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder bb = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bb.addVertex(m, -hw,  hh, z).setColor(r, g, b, a);
        bb.addVertex(m, -hw, -hh, z).setColor(r, g, b, a);
        bb.addVertex(m,  hw, -hh, z).setColor(r, g, b, a);
        bb.addVertex(m,  hw,  hh, z).setColor(r, g, b, a);
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }
}
