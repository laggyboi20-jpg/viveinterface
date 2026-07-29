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
 *   <li>Hand/head anchors: follow the live body pose via a {@link Placement} (offset + rotation),
 *       so the panel rides the hand like a VR item HUD and never clips through it.</li>
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

    /** Hand/head offset + rotation. */
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
        };
    }

    /** Apply {@link #place} on top of a live body pose: rotate, then translate in the rotated frame. */
    private Resolved fromBody(VrPoses.BodyPose body) {
        if (body == null) return null;
        Quaternionf rot = new Quaternionf(body.rot()).mul(place.rotation());
        Vector3f off = rot.transform(new Vector3f(place.posX, place.posY, place.posZ));
        Vec3 p = body.pos();
        Vector3f pos = new Vector3f((float) p.x + off.x, (float) p.y + off.y, (float) p.z + off.z);
        return new Resolved(pos, rot);
    }

    /** Snap to a hand/head anchor with the configured default placement (ammo-HUD style — no clipping). */
    public void anchorToBody(PanelAnchor bodyAnchor) {
        this.anchor = bodyAnchor;
        com.laggy.viveinterface.config.ViveConfig cfg = com.laggy.viveinterface.config.ViveConfig.get();
        Placement def = switch (bodyAnchor) {
            case MAIN_HAND -> cfg.handPanelPlace;
            case OFF_HAND -> cfg.heldPanelPlace;
            case HEAD -> cfg.headPanelPlace;
            default -> new Placement();
        };
        this.place = def.copy();
        this.scale = def.scale;   // configurable default size for this anchor
    }

    /** Freeze the current resolved transform into a WORLD anchor (used on "drop"). */
    public void dropToWorld() {
        Resolved r = resolve();
        if (r == null) return;
        anchor = PanelAnchor.WORLD;
        worldPos.set(r.pos);
        worldRot.set(r.rot);
        userOffset.set(0, 0, 0);   // 0,0,0 is now "where it was left"
    }

    public record Resolved(Vector3f pos, Quaternionf rot) {}
}
