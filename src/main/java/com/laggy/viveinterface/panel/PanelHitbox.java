package com.laggy.viveinterface.panel;

import org.joml.Vector3f;

/**
 * Cheap hand-vs-panel collision, used only while a panel is being reached for / carried — NOT a
 * continuous physics sim. A panel is a thin oriented box (its quad + a little thickness); a hand is a
 * sphere. We test the sphere against the box each frame only for the few placed panels, and once the
 * panel is released its transform is baked to a static one (see Panel.attachToBody / dropToWorld),
 * so nothing keeps running afterwards.
 */
public final class PanelHitbox {

    private static final float THICKNESS = 0.03f;   // half-depth of a panel's box, metres

    private PanelHitbox() {}

    /** Distance from a hand point to the panel's oriented box surface (0 if inside), or +inf if unposed. */
    public static float distance(Panel p, Vector3f hand) {
        Panel.Resolved r = p.resolve();
        if (r == null) return Float.POSITIVE_INFINITY;

        Vector3f rel = new Vector3f(hand).sub(r.pos());
        Vector3f right = r.rot().transform(new Vector3f(1, 0, 0));
        Vector3f up = r.rot().transform(new Vector3f(0, 1, 0));
        Vector3f normal = r.rot().transform(new Vector3f(0, 0, 1));

        float du = rel.dot(right), dv = rel.dot(up), dn = rel.dot(normal);
        float hw = p.effectiveWidth() * 0.5f, hh = p.effectiveHeight() * 0.5f;

        // Vector from the closest point on the box to the hand, in box-local axes.
        float ou = du - clamp(du, hw), ov = dv - clamp(dv, hh), on = dn - clamp(dn, THICKNESS);
        return (float) Math.sqrt(ou * ou + ov * ov + on * on);
    }

    /** The placed panel whose box the hand sphere overlaps, nearest first; null if none touched. */
    public static Panel nearestTouched(java.util.List<Panel> panels, Vector3f hand, float radius) {
        Panel best = null;
        float bestDist = radius;
        for (Panel p : panels) {
            float d = distance(p, hand);
            if (d <= bestDist) { bestDist = d; best = p; }
        }
        return best;
    }

    private static float clamp(float v, float half) {
        return v < -half ? -half : (v > half ? half : v);
    }
}
