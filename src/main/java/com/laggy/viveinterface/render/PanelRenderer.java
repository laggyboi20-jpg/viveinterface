package com.laggy.viveinterface.render;

import com.laggy.viveinterface.config.ViveConfig;
import com.laggy.viveinterface.cut.CutTool;
import com.laggy.viveinterface.debug.DebugLog;
import com.laggy.viveinterface.panel.Panel;
import com.laggy.viveinterface.panel.PanelManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/**
 * Draws every placed panel in the world as a textured quad sampling the live HUD snapshot.
 *
 * <p><b>How this works on 26.2.</b> The drawing model changed completely: you no longer grab a
 * {@code VertexConsumer} from a {@code MultiBufferSource} and draw immediately — you <i>submit</i>
 * geometry and the renderer batches it. So:
 *
 * <ul>
 *   <li>the hook is {@code LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN}, which hands us a
 *       {@link LevelRenderContext} with a {@code SubmitNodeCollector} and a {@link PoseStack};</li>
 *   <li>each panel is one {@code submitCustomGeometry(...)} call with a stock
 *       {@code RenderTypes.entityTranslucent} render type;</li>
 *   <li>that render type wants a registered texture id, so {@link SnapshotTexture} publishes the
 *       snapshot under {@code viveinterface:hud_snapshot} — which avoids hand-writing a
 *       {@code RenderPipeline} entirely.</li>
 * </ul>
 *
 * <p>Panels sample the snapshot rather than the live framebuffer, because {@link HudMask} may punch
 * holes in the latter.
 */
public final class PanelRenderer {

    /** Packed lightmap for "fully lit" (block 15, sky 15). 26.2 dropped LightTexture.FULL_BRIGHT. */
    private static final int FULL_BRIGHT = 0xF000F0;

    private PanelRenderer() {}

    public static void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(PanelRenderer::onRender);
        DebugLog.log("RENDER", "world panel rendering registered (LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN)");
    }

    private static void onRender(LevelRenderContext ctx) {
        if (PanelManager.all().isEmpty()) return;
        if (!GuiSnapshot.ready() || !SnapshotTexture.sync()) return;

        Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
        PoseStack pose = ctx.poseStack();
        RenderType type = RenderTypes.entityTranslucent(SnapshotTexture.ID);

        int bg = ViveConfig.get().backgroundColor;
        int bgA = (bg >>> 24) & 0xFF;
        Panel touched = CutTool.get().touchedPanel();

        for (Panel p : PanelManager.all()) {
            Panel.Resolved r = p.resolve();
            if (r == null) continue;

            float hw = p.effectiveWidth() * 0.5f;
            float hh = p.effectiveHeight() * 0.5f;
            // Framebuffer textures are bottom-left origin, so flip V.
            float u0 = p.u0, u1 = p.u1, v0 = 1f - p.v0, v1 = 1f - p.v1;

            pose.pushPose();
            pose.translate(r.pos().x - cam.x, r.pos().y - cam.y, r.pos().z - cam.z);
            pose.mulPose(r.rot());

            // Opaque backing first, so translucent parts of the HUD don't show the world through them.
            if (bgA > 0) {
                submitQuad(ctx, pose, type, hw, hh, -0.001f, u0, v0, u1, v1,
                        (bg >> 16) & 0xFF, (bg >> 8) & 0xFF, bg & 0xFF, bgA, true);
            }
            submitQuad(ctx, pose, type, hw, hh, 0f, u0, v0, u1, v1, 255, 255, 255, 255, false);
            if (p == touched) {
                // Grabbable → green wash, just in front.
                submitQuad(ctx, pose, type, hw, hh, 0.002f, u0, v0, u1, v1, 51, 255, 77, 77, true);
            }

            pose.popPose();
        }

        DebugLog.trace("RENDER", "submitted %d panel(s)", PanelManager.all().size());
    }

    /**
     * One double-sided quad. {@code flat} collapses the UVs to a single texel so the quad reads as a
     * plain colour — used for the backing and the green tint, which reuse the same render type rather
     * than needing a second one.
     */
    private static void submitQuad(LevelRenderContext ctx, PoseStack pose, RenderType type,
                                   float hw, float hh, float z,
                                   float u0, float v0, float u1, float v1,
                                   int r, int g, int b, int a, boolean flat) {
        float su0 = flat ? 0f : u0, su1 = flat ? 0f : u1;
        float sv0 = flat ? 0f : v0, sv1 = flat ? 0f : v1;

        ctx.submitNodeCollector().submitCustomGeometry(pose, type, (p, vc) -> {
            // Front face.
            quad(vc, p, hw, hh, z, su0, su1, sv0, sv1, r, g, b, a, 1f);
            // Back face (panels are double-sided; entityTranslucent culls, so add the reverse winding).
            quadBack(vc, p, hw, hh, z, su0, su1, sv0, sv1, r, g, b, a);
        });
    }

    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer vc, PoseStack.Pose p,
                             float hw, float hh, float z, float u0, float u1, float v0, float v1,
                             int r, int g, int b, int a, float nz) {
        vert(vc, p, -hw,  hh, z, u0, v0, r, g, b, a, nz);
        vert(vc, p, -hw, -hh, z, u0, v1, r, g, b, a, nz);
        vert(vc, p,  hw, -hh, z, u1, v1, r, g, b, a, nz);
        vert(vc, p,  hw,  hh, z, u1, v0, r, g, b, a, nz);
    }

    private static void quadBack(com.mojang.blaze3d.vertex.VertexConsumer vc, PoseStack.Pose p,
                                 float hw, float hh, float z, float u0, float u1, float v0, float v1,
                                 int r, int g, int b, int a) {
        vert(vc, p,  hw,  hh, z, u1, v0, r, g, b, a, -1f);
        vert(vc, p,  hw, -hh, z, u1, v1, r, g, b, a, -1f);
        vert(vc, p, -hw, -hh, z, u0, v1, r, g, b, a, -1f);
        vert(vc, p, -hw,  hh, z, u0, v0, r, g, b, a, -1f);
    }

    private static void vert(com.mojang.blaze3d.vertex.VertexConsumer vc, PoseStack.Pose p,
                             float x, float y, float z, float u, float v,
                             int r, int g, int b, int a, float nz) {
        vc.addVertex(p, x, y, z)
          .setColor(r, g, b, a)
          .setUv(u, v)
          .setOverlay(OverlayTexture.NO_OVERLAY)
          .setLight(FULL_BRIGHT)                   // HUD panels are self-lit, like the flat panel
          .setNormal(p, 0f, 0f, nz);
    }

    /** Placed-panel count, for logging. */
    public static int placedCount() {
        return PanelManager.all().size();
    }

}
