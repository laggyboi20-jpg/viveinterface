package com.laggy.viveinterface.panel;

import com.laggy.viveinterface.render.GuiTexture;
import com.laggy.viveinterface.vr.VrPoses;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A placed slice of the HUD: a UV rectangle over the GUI framebuffer plus a transform.
 *
 * <ul>
 *   <li>{@link PanelAnchor#WORLD}: fixed at {@link #worldPos}/{@link #worldRot}, nudged by
 *       {@link #userOffset} (0,0,0 = where it was left).</li>
 *   <li>Hand/head anchors: follow the live body pose via {@link #relPos}/{@link #relRot}, so the
 *       panel rides the body part exactly as it was placed.</li>
 *   <li>{@link PanelAnchor#PANEL}: rides another piece, so a cluster moves as one.</li>
 * </ul>
 */
public final class Panel {

    /** UV rectangle in screen fractions, top-left origin, each in [0,1]. */
    public float u0, v0, u1, v1;

    public PanelAnchor anchor;

    /** WORLD transform. */
    public final Vector3f worldPos = new Vector3f();
    public final Quaternionf worldRot = new Quaternionf();
    /** WORLD position nudge in the panel's local frame (settings screen). 0 = as left. */
    public final Vector3f userOffset = new Vector3f();

    /** Legacy euler offset, kept only so older {@code panels.json} files still load. */
    public Placement place = new Placement();

    /** Base physical width in metres (height derived from UV aspect); multiplied by {@link #scale}. */
    public float widthMeters = 0.30f;
    /** Uniform user size multiplier. */
    public float scale = 1f;

    public Panel(float u0, float v0, float u1, float v1, PanelAnchor anchor) {
        this.u0 = u0; this.v0 = v0; this.u1 = u1; this.v1 = v1;
        this.anchor = anchor;
    }

    private float baseHeight() {
        int tw = GuiTexture.width();
        int th = GuiTexture.height();
        if (tw <= 0 || th <= 0) return widthMeters;
        float pxW = Math.abs(u1 - u0) * tw;
        float pxH = Math.abs(v1 - v0) * th;
        if (pxW <= 0.0001f) return widthMeters;
        return widthMeters * (pxH / pxW);
    }

    public float effectiveWidth() { return widthMeters * scale; }
    public float effectiveHeight() { return baseHeight() * scale; }

    public boolean isHandAnchored() {
        return anchor == PanelAnchor.MAIN_HAND || anchor == PanelAnchor.OFF_HAND || anchor == PanelAnchor.HEAD;
    }

    /** Resolved world transform for this frame. Returns null if an anchor's pose is unavailable. */
    public Resolved resolve() {
        return switch (anchor) {
            case WORLD -> {
                Quaternionf rot = new Quaternionf(worldRot);
                Vector3f pos = new Vector3f(worldPos).add(rot.transform(new Vector3f(userOffset)));
                yield new Resolved(pos, rot);
            }
            case MAIN_HAND -> fromBody(VrPoses.mainHand());
            case OFF_HAND -> fromBody(VrPoses.offHand());
            case HEAD -> fromBody(VrPoses.head());
            case PANEL -> fromParent();
        };
    }

    /** Ride the parent piece: the same relative transform, applied to wherever the parent resolved to. */
    private Resolved fromParent() {
        Panel parent = PanelManager.byId(parentId);
        if (parent == null || resolving) return null;   // orphaned, or a cycle — draw nothing
        resolving = true;
        try {
            Resolved p = parent.resolve();
            if (p == null) return null;
            Quaternionf rot = new Quaternionf(p.rot()).mul(relRot);
            Vector3f off = new Quaternionf(p.rot()).transform(new Vector3f(relPos));
            return new Resolved(new Vector3f(p.pos()).add(off), rot);
        } finally {
            resolving = false;
        }
    }

    /** Would attaching this piece to {@code candidate} create a loop of pieces? */
    public boolean wouldCycle(Panel candidate) {
        Panel p = candidate;
        for (int guard = 0; p != null && guard < 64; guard++) {
            if (p == this) return true;
            p = (p.anchor == PanelAnchor.PANEL) ? PanelManager.byId(p.parentId) : null;
        }
        return false;
    }

    /** Stick to another piece, keeping the current position/orientation. */
    public void attachToPanel(Panel parent) {
        Resolved r = resolve();
        Resolved pr = (parent == null) ? null : parent.resolve();
        if (r == null || pr == null) return;
        Quaternionf inv = new Quaternionf(pr.rot()).conjugate();
        relPos.set(inv.transform(new Vector3f(r.pos()).sub(pr.pos())));
        relRot.set(new Quaternionf(inv).mul(r.rot()));
        this.parentId = parent.id;
        this.anchor = PanelAnchor.PANEL;
    }

    /**
     * Body-relative transform used by every non-WORLD anchor: {@code world = bodyPos + bodyRot*relPos}
     * and {@code worldRot = bodyRot * relRot}. Kept as a quaternion (not the euler {@link Placement})
     * so grabbing a piece can preserve its exact orientation instead of snapping to a preset.
     */
    public final Vector3f relPos = new Vector3f();
    public final Quaternionf relRot = new Quaternionf();

    /** Stable identity, so a piece stuck to another piece can find its parent again after a reload. */
    public java.util.UUID id = java.util.UUID.randomUUID();
    /** Parent piece for {@link PanelAnchor#PANEL}; null otherwise. */
    public java.util.UUID parentId;

    /** Guards against a cycle of pieces stuck to each other resolving forever. */
    private boolean resolving;

    /** Apply the body-relative transform on top of a live body pose. */
    private Resolved fromBody(VrPoses.BodyPose body) {
        if (body == null) return null;
        Quaternionf rot = new Quaternionf(body.rot()).mul(relRot);
        Vector3f off = new Quaternionf(body.rot()).transform(new Vector3f(relPos));
        Vec3 p = body.pos();
        Vector3f pos = new Vector3f((float) p.x + off.x, (float) p.y + off.y, (float) p.z + off.z);
        return new Resolved(pos, rot);
    }

    /** Bake {@link #place} (the configured euler preset) into {@link #relPos}/{@link #relRot}. */
    public void applyPlacement() {
        relRot.set(place.rotation());
        relPos.set(new Quaternionf(relRot).transform(new Vector3f(place.posX, place.posY, place.posZ)));
    }

    /**
     * Attach to a body part <b>keeping the piece exactly where it currently is</b> (used when a VR hand
     * grabs it, so it doesn't snap to a preset offset).
     */
    public void attachToBody(PanelAnchor bodyAnchor, VrPoses.BodyPose body) {
        Resolved r = resolve();
        if (body == null || r == null) return;
        Quaternionf inv = new Quaternionf(body.rot()).conjugate();
        Vec3 bp = body.pos();
        relPos.set(inv.transform(new Vector3f(
                r.pos().x - (float) bp.x, r.pos().y - (float) bp.y, r.pos().z - (float) bp.z)));
        relRot.set(new Quaternionf(inv).mul(r.rot()));
        this.anchor = bodyAnchor;
        this.parentId = null;
        // Keep it beside the limb rather than buried halfway inside your arm: the piece's centre has
        // to sit at least a hand's-width plus the configured gap away from the joint.
        com.laggy.viveinterface.config.ViveConfig cfg = com.laggy.viveinterface.config.ViveConfig.get();
        SurfaceSnap.clearOfBody(this, cfg.grabRadius + cfg.surfaceClearance);
    }

    /** Freeze the current resolved transform into a WORLD anchor (used on "drop"). */
    public void dropToWorld() {
        Resolved r = resolve();
        if (r == null) return;
        anchor = PanelAnchor.WORLD;
        parentId = null;
        worldPos.set(r.pos);
        worldRot.set(r.rot);
        userOffset.set(0, 0, 0);   // 0,0,0 is now "where it was left"
    }

    public record Resolved(Vector3f pos, Quaternionf rot) {}
}
