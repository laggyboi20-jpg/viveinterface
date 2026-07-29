package com.laggy.viveinterface.render;

import com.laggy.viveinterface.config.ViveConfig;
import com.laggy.viveinterface.cut.CutTool;
import com.laggy.viveinterface.panel.Panel;
import com.laggy.viveinterface.panel.PanelManager;
import com.laggy.viveinterface.panel.Placement;
import com.laggy.viveinterface.vr.VrPoses;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/** Draws every placed panel — plus the cut-mode paper, selection highlight and sword. */
public final class PanelRenderer {

    private PanelRenderer() {}

    public static void register() {
        WorldRenderEvents.END.register(PanelRenderer::onRender);
    }

    private static void onRender(WorldRenderContext ctx) {
        // Panels sample the snapshot (full HUD), not the live framebuffer (which gets holes punched
        // into it by HudMask so cut regions vanish from Vivecraft's flat panel).
        if (!VrPoses.vrActive() || !GuiSnapshot.ready()) return;
        int texId = GuiSnapshot.texId();
        if (texId == 0) return;

        Vec3 cam = ctx.camera().getPosition();
        PoseStack ps = ctx.matrixStack();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();          // panels are double-sided
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        CutTool cut = CutTool.get();
        Panel touched = cut.touchedPanel();
        for (Panel p : PanelManager.all()) {
            renderPanel(ps, cam, p, texId);
            if (p == touched) renderTint(ps, cam, p, 0.2f, 1f, 0.3f, 0.30f);  // grabbable → green
        }

        if (cut.active() && cut.paper() != null) {
            Panel paper = cut.paper();
            renderBackground(ps, cam, paper);            // solid backing behind the HUD
            renderPanel(ps, cam, paper, texId);          // the HUD itself
            renderTrail(ps, cam, paper, cut.trail());    // green/red sword path
            renderBlade(ps, cam);
            renderSelectStick(ps, cam);                  // off-hand pointer for grabbing pieces
            renderHandHitbox(ps, cam);                   // the grab volume on the off hand
        }

        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void renderPanel(PoseStack ps, Vec3 cam, Panel panel, int texId) {
        Panel.Resolved r = panel.resolve();
        if (r == null) return;

        float w = panel.effectiveWidth();
        float h = panel.effectiveHeight();
        float hw = w * 0.5f, hh = h * 0.5f;

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

    /** Opaque backing so the transparent parts of the HUD read as a solid sheet. */
    private static void renderBackground(PoseStack ps, Vec3 cam, Panel paper) {
        float pw = paper.effectiveWidth(), ph = paper.effectiveHeight();
        float hw = pw * 0.5f + 0.01f, hh = ph * 0.5f + 0.01f; // small margin border
        float z = -0.002f;                                     // just behind the HUD texture

        ps.pushPose();
        ps.translate(paper.worldPos.x - cam.x, paper.worldPos.y - cam.y, paper.worldPos.z - cam.z);
        ps.mulPose(new Quaternionf(paper.worldRot));
        Matrix4f m = ps.last().pose();

        RenderSystem.setShader(CoreShaders.POSITION_COLOR);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder bb = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float r = 0.11f, g = 0.11f, b = 0.14f, a = 0.96f;      // dark slate
        bb.addVertex(m, -hw,  hh, z).setColor(r, g, b, a);
        bb.addVertex(m, -hw, -hh, z).setColor(r, g, b, a);
        bb.addVertex(m,  hw, -hh, z).setColor(r, g, b, a);
        bb.addVertex(m,  hw,  hh, z).setColor(r, g, b, a);
        BufferUploader.drawWithShader(bb.buildOrThrow());
        ps.popPose();
    }

    /** The sword-tip path on the paper: green where in bounds, red where it strayed off. */
    private static void renderTrail(PoseStack ps, Vec3 cam, Panel paper, List<CutTool.TrailPoint> trail) {
        if (trail.size() < 2) return;
        float halfW = 0.004f;
        float z = 0.003f; // in front of the HUD + selection

        ps.pushPose();
        ps.translate(paper.worldPos.x - cam.x, paper.worldPos.y - cam.y, paper.worldPos.z - cam.z);
        ps.mulPose(new Quaternionf(paper.worldRot));
        Matrix4f m = ps.last().pose();

        RenderSystem.setShader(CoreShaders.POSITION_COLOR);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder bb = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 1; i < trail.size(); i++) {
            CutTool.TrailPoint b0 = trail.get(i - 1);
            CutTool.TrailPoint b1 = trail.get(i);
            if (!b1.connected()) continue; // pen was lifted between these — no line

            float dx = b1.x() - b0.x(), dy = b1.y() - b0.y();
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-5f) continue;
            float px = -dy / len * halfW, py = dx / len * halfW; // in-plane perpendicular

            boolean ok = b0.inBounds() && b1.inBounds();
            float r = ok ? 0.15f : 1.0f, g = ok ? 1.0f : 0.2f, bl = ok ? 0.3f : 0.2f;
            bb.addVertex(m, b0.x() + px, b0.y() + py, z).setColor(r, g, bl, 0.9f);
            bb.addVertex(m, b0.x() - px, b0.y() - py, z).setColor(r, g, bl, 0.9f);
            bb.addVertex(m, b1.x() - px, b1.y() - py, z).setColor(r, g, bl, 0.9f);
            bb.addVertex(m, b1.x() + px, b1.y() + py, z).setColor(r, g, bl, 0.9f);
        }
        BufferUploader.drawWithShader(bb.buildOrThrow());
        ps.popPose();
    }

    private static final ItemStack SWORD_MODEL = new ItemStack(Items.WOODEN_SWORD);
    private static final ItemStack STICK_MODEL = new ItemStack(Items.STICK);

    /** Wooden sword on the dominant hand — real item model, or a brown quad fallback. */
    private static void renderBlade(PoseStack ps, Vec3 cam) {
        VrPoses.BodyPose hand = VrPoses.mainHand();
        if (hand == null) return;
        float len = ViveConfig.get().bladeLength;

        if (ViveConfig.get().realModels) {
            renderHandItem(ps, cam, hand, SWORD_MODEL, true, ViveConfig.get().swordPlace);
            return;
        }

        Vec3 p = hand.pos();
        Vec3 d = hand.dir();
        Vector3f base = new Vector3f((float) p.x, (float) p.y, (float) p.z);
        Vector3f tip = new Vector3f(
                base.x + (float) d.x * len, base.y + (float) d.y * len, base.z + (float) d.z * len);

        Vector3f up = hand.rot().transform(new Vector3f(0, 1, 0)).mul(0.012f);
        Vector3f side = hand.rot().transform(new Vector3f(1, 0, 0)).mul(0.012f);

        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        RenderSystem.setShader(CoreShaders.POSITION_COLOR);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder bb = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        colorQuad(bb, m, base, tip, up, 0.62f, 0.44f, 0.24f);
        colorQuad(bb, m, base, tip, side, 0.62f, 0.44f, 0.24f);
        BufferUploader.drawWithShader(bb.buildOrThrow());
        ps.popPose();
    }

    /**
     * Render a real item model held at the controller, positioned by a fully-configurable
     * {@link Placement} (XYZ offset + yaw/pitch/roll + scale) on top of the live hand pose. Tune it in
     * the settings screen; flip "real item models" off for the coloured-quad fallback.
     */
    private static void renderHandItem(PoseStack ps, Vec3 cam, VrPoses.BodyPose hand,
                                       ItemStack stack, boolean rightHand, Placement pl) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 p = hand.pos();
        float s = Math.max(0.01f, pl.scale);

        ps.pushPose();
        ps.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
        ps.mulPose(hand.rot());
        ps.mulPose(pl.rotation());                 // configurable yaw/pitch/roll
        ps.translate(pl.posX, pl.posY, pl.posZ);   // configurable offset (in the rotated frame)
        ps.scale(s, s, s);

        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        mc.getItemRenderer().renderStatic(stack,
                rightHand ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ps, buf, mc.level, 0);
        buf.endBatch();
        ps.popPose();
    }

    /** Short pointer stick on the off hand + a dot at the tip (the grab point). */
    private static void renderSelectStick(PoseStack ps, Vec3 cam) {
        VrPoses.BodyPose hand = VrPoses.offHand();
        if (hand == null) return;
        float len = ViveConfig.get().selectStickLength;

        if (ViveConfig.get().realModels) {
            renderHandItem(ps, cam, hand, STICK_MODEL, false, ViveConfig.get().stickPlace);
            // still draw just the tip dot below so the grab point is visible
        }

        Vec3 p = hand.pos();
        Vec3 d = hand.dir();
        Vector3f base = new Vector3f((float) p.x, (float) p.y, (float) p.z);
        Vector3f tip = new Vector3f(
                base.x + (float) d.x * len, base.y + (float) d.y * len, base.z + (float) d.z * len);

        Vector3f up = hand.rot().transform(new Vector3f(0, 1, 0)).mul(0.008f);
        Vector3f side = hand.rot().transform(new Vector3f(1, 0, 0)).mul(0.008f);
        Vector3f tipUp = hand.rot().transform(new Vector3f(0, 1, 0)).mul(0.02f);
        Vector3f tipSide = hand.rot().transform(new Vector3f(1, 0, 0)).mul(0.02f);

        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();

        boolean realStick = ViveConfig.get().realModels;
        RenderSystem.setShader(CoreShaders.POSITION_COLOR);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder bb = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        if (!realStick) {                 // the item model already draws the shaft
            colorQuad(bb, m, base, tip, up, 0.2f, 0.9f, 1.0f);
            colorQuad(bb, m, base, tip, side, 0.2f, 0.9f, 1.0f);
        }
        // a small bright quad at the tip marks the exact grab point (always shown)
        bb.addVertex(m, tip.x - tipSide.x, tip.y - tipUp.y, tip.z - tipSide.z).setColor(0.7f, 1f, 1f, 1f);
        bb.addVertex(m, tip.x - tipSide.x, tip.y + tipUp.y, tip.z - tipSide.z).setColor(0.7f, 1f, 1f, 1f);
        bb.addVertex(m, tip.x + tipSide.x, tip.y + tipUp.y, tip.z + tipSide.z).setColor(0.7f, 1f, 1f, 1f);
        bb.addVertex(m, tip.x + tipSide.x, tip.y - tipUp.y, tip.z + tipSide.z).setColor(0.7f, 1f, 1f, 1f);
        BufferUploader.drawWithShader(bb.buildOrThrow());
        ps.popPose();
    }

    /** A translucent cube on the off hand showing the grab volume (radius = ViveConfig.grabRadius). */
    private static void renderHandHitbox(PoseStack ps, Vec3 cam) {
        VrPoses.BodyPose hand = VrPoses.offHand();
        if (hand == null) return;
        float h = ViveConfig.get().grabRadius;
        Vec3 c = hand.pos();

        ps.pushPose();
        ps.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
        Matrix4f m = ps.last().pose();
        RenderSystem.setShader(CoreShaders.POSITION_COLOR);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder bb = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float r = 0.3f, g = 0.8f, b = 1f, a = 0.18f;
        // six faces of an axis-aligned cube [-h,h]^3
        face(bb, m, h, a, r, g, b, 0); face(bb, m, h, a, r, g, b, 1);
        face(bb, m, h, a, r, g, b, 2); face(bb, m, h, a, r, g, b, 3);
        face(bb, m, h, a, r, g, b, 4); face(bb, m, h, a, r, g, b, 5);
        BufferUploader.drawWithShader(bb.buildOrThrow());
        ps.popPose();
    }

    private static void face(BufferBuilder bb, Matrix4f m, float h, float a, float r, float g, float b, int f) {
        float[][] q = switch (f) {
            case 0 -> new float[][]{{-h,-h, h},{-h, h, h},{ h, h, h},{ h,-h, h}}; // +Z
            case 1 -> new float[][]{{ h,-h,-h},{ h, h,-h},{-h, h,-h},{-h,-h,-h}}; // -Z
            case 2 -> new float[][]{{ h,-h, h},{ h, h, h},{ h, h,-h},{ h,-h,-h}}; // +X
            case 3 -> new float[][]{{-h,-h,-h},{-h, h,-h},{-h, h, h},{-h,-h, h}}; // -X
            case 4 -> new float[][]{{-h, h, h},{-h, h,-h},{ h, h,-h},{ h, h, h}}; // +Y
            default -> new float[][]{{-h,-h,-h},{-h,-h, h},{ h,-h, h},{ h,-h,-h}}; // -Y
        };
        for (float[] v : q) bb.addVertex(m, v[0], v[1], v[2]).setColor(r, g, b, a);
    }

    private static void colorQuad(BufferBuilder bb, Matrix4f m, Vector3f base, Vector3f tip, Vector3f w,
                                  float r, float g, float b) {
        bb.addVertex(m, base.x - w.x, base.y - w.y, base.z - w.z).setColor(r, g, b, 1f);
        bb.addVertex(m, base.x + w.x, base.y + w.y, base.z + w.z).setColor(r, g, b, 1f);
        bb.addVertex(m, tip.x + w.x, tip.y + w.y, tip.z + w.z).setColor(r, g, b, 1f);
        bb.addVertex(m, tip.x - w.x, tip.y - w.y, tip.z - w.z).setColor(r, g, b, 1f);
    }
}
