package com.laggy.viveinterface.render;

import com.laggy.viveinterface.cut.CutTool;
import com.laggy.viveinterface.panel.Panel;
import com.laggy.viveinterface.panel.PanelManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
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
        PoseStack ps = ctx.matrixStack();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();          // panels are double-sided
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        Panel touched = CutTool.get().touchedPanel();
        for (Panel p : PanelManager.all()) {
            renderPanel(ps, cam, p, texId);
            if (p == touched) renderTint(ps, cam, p, 0.2f, 1f, 0.3f, 0.30f);  // grabbable → green
        }

        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void renderPanel(PoseStack ps, Vec3 cam, Panel panel, int texId) {
        Panel.Resolved r = panel.resolve();
        if (r == null) return;

        float hw = panel.effectiveWidth() * 0.5f;
        float hh = panel.effectiveHeight() * 0.5f;

        ps.pushPose();
        ps.translate(r.pos().x - cam.x, r.pos().y - cam.y, r.pos().z - cam.z);
        ps.mulPose(r.rot());
        Matrix4f m = ps.last().pose();

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

        ps.popPose();
    }

    /** Translucent colour wash over a panel (e.g. green when the hand is colliding with it). */
    private static void renderTint(PoseStack ps, Vec3 cam, Panel panel, float r, float g, float b, float a) {
        Panel.Resolved res = panel.resolve();
        if (res == null) return;
        float hw = panel.effectiveWidth() * 0.5f, hh = panel.effectiveHeight() * 0.5f;
        float z = 0.002f; // just in front of the panel

        ps.pushPose();
        ps.translate(res.pos().x - cam.x, res.pos().y - cam.y, res.pos().z - cam.z);
        ps.mulPose(res.rot());
        Matrix4f m = ps.last().pose();
        RenderSystem.setShader(CoreShaders.POSITION_COLOR);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder bb = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bb.addVertex(m, -hw,  hh, z).setColor(r, g, b, a);
        bb.addVertex(m, -hw, -hh, z).setColor(r, g, b, a);
        bb.addVertex(m,  hw, -hh, z).setColor(r, g, b, a);
        bb.addVertex(m,  hw,  hh, z).setColor(r, g, b, a);
        BufferUploader.drawWithShader(bb.buildOrThrow());
        ps.popPose();
    }
}
