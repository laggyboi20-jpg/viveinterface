package com.laggy.viveinterface.cut;

import com.laggy.viveinterface.config.ViveConfig;
import com.laggy.viveinterface.debug.DebugLog;
import com.laggy.viveinterface.panel.Panel;
import com.laggy.viveinterface.panel.PanelAnchor;
import com.laggy.viveinterface.panel.PanelHitbox;
import com.laggy.viveinterface.panel.PanelManager;
import com.laggy.viveinterface.panel.PanelStore;
import com.laggy.viveinterface.vr.VrPoses;
import com.laggy.viveinterface.vr.VrTriggers;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The VR cutting flow:
 *
 * <ol>
 *   <li>Toggle key → a "menu" showing the full HUD spawns in front of you; a wooden sword appears on
 *       your dominant hand (ARMED). Cut mode is fully modal — every other button is inert.</li>
 *   <li>Hold the <b>right trigger</b> and swipe the sword through the menu. The <b>whole blade</b>
 *       cuts wherever it crosses the paper, leaving a green line (in bounds) / red line (off edge).</li>
 *   <li>When one cut stroke reaches <b>two edges</b> of the menu, that region detaches and floats
 *       right where you cut it. The menu stays put (CUT_READY).</li>
 *   <li>Bring your <b>left hand</b> to the floating piece to grab it → the menu disappears and the
 *       piece rides your off hand (HOLDING).</li>
 *   <li><b>Left trigger</b> → let go; the piece stays where your hand is. A fresh menu re-arms so you
 *       can keep cutting. Toggle key leaves cut mode entirely.</li>
 * </ol>
 */
public final class CutTool {

    public enum State { OFF, ARMED, CUTTING, CUT_READY, HOLDING }

    // Tunables (blade/stick length, radii, menu size) now live in ViveConfig; these stay fixed.
    private static final float EDGE_MARGIN = 0.06f;   // how near an edge counts as "reached"
    private static final float MIN_CUT_UV = 0.03f;    // ignore degenerate slivers
    private static final int TRAIL_MAX = 4000;

    /** One dab of the blade on the paper, in paper-local metres. */
    public record TrailPoint(float x, float y, boolean inBounds, boolean connected) {}

    private static final CutTool INSTANCE = new CutTool();
    public static CutTool get() { return INSTANCE; }

    private State state = State.OFF;
    private Panel paper;      // the menu (full-HUD), shown ARMED..CUT_READY, hidden once grabbed
    private Panel held;       // the slice riding the off hand

    private final java.util.List<TrailPoint> trail = new java.util.ArrayList<>();
    private boolean penDown = false;
    private final boolean[] edges = new boolean[4];   // left, right, top, bottom
    private float cutMinU, cutMinV, cutMaxU, cutMaxV;

    private boolean prevCut = false;
    private boolean prevRelease = false;

    public State state() { return state; }
    public boolean active() { return state != State.OFF; }
    public Panel paper() { return paper; }
    public java.util.List<TrailPoint> trail() { return trail; }

    /** The placed panel the off hand is currently reaching into (grabbable) — for the green tint. */
    public Panel touchedPanel() {
        if (state != State.ARMED && state != State.CUT_READY) return null;
        VrPoses.BodyPose off = VrPoses.offHand();
        if (off == null) return null;
        Vec3 hp = off.pos();
        Vector3f hand = new Vector3f((float) hp.x, (float) hp.y, (float) hp.z);
        return PanelHitbox.nearestTouched(PanelManager.all(), hand, ViveConfig.get().grabRadius);
    }

    // --- key actions ---------------------------------------------------------

    public void toggle() {
        if (state == State.OFF) enterArmed();
        else exit();
    }

    /** Desktop fallback for the left trigger. */
    public void releaseHeld() {
        if (state == State.HOLDING) doRelease();
    }

    /** Move the carried piece to the other hand (bind your A button to this). */
    public void changeHand() {
        if (state != State.HOLDING || held == null) return;
        PanelAnchor next = (held.anchor == PanelAnchor.MAIN_HAND)
                ? PanelAnchor.OFF_HAND : PanelAnchor.MAIN_HAND;
        held.anchorToBody(next);
        DebugLog.logf("HAND", "piece moved to %s", next);
        VrPoses.haptic(next == PanelAnchor.MAIN_HAND, 0.6f);
    }

    // --- per-frame update ----------------------------------------------------

    public void tick() {
        if (state == State.OFF) return;

        boolean cutDown = VrTriggers.cut();
        boolean relDown = VrTriggers.release();
        boolean cutRising = cutDown && !prevCut;
        boolean relRising = relDown && !prevRelease;

        switch (state) {
            case ARMED -> {
                if (cutRising) startCut();
                else tryGrab();                         // reposition a placed piece with the stick
            }
            case CUTTING -> {
                updateBladeCut();                       // may finalise into CUT_READY
                if (state == State.CUTTING && !cutDown) cancelCut();
            }
            case CUT_READY -> tryGrab();
            case HOLDING -> {
                // Either trigger lets go — the off-hand/USE mapping is unreliable across setups.
                if (cutRising || relRising) doRelease();
            }
            default -> { }
        }

        prevCut = cutDown;
        prevRelease = relDown;
    }

    // --- cutting -------------------------------------------------------------

    private void startCut() {
        state = State.CUTTING;
        resetStroke();
    }

    private void cancelCut() {
        state = State.ARMED;
        resetStroke();
    }

    /** Intersect the whole blade segment with the menu plane and record where it slices. */
    private void updateBladeCut() {
        if (paper == null) return;
        VrPoses.BodyPose hand = VrPoses.mainHand();
        if (hand == null) return;

        float bladeLen = ViveConfig.get().bladeLength;
        Vec3 p = hand.pos();
        Vec3 d = hand.dir();
        Vector3f base = new Vector3f((float) p.x, (float) p.y, (float) p.z);
        Vector3f tip = new Vector3f(
                base.x + (float) d.x * bladeLen,
                base.y + (float) d.y * bladeLen,
                base.z + (float) d.z * bladeLen);

        Vector3f right = paper.worldRot.transform(new Vector3f(1, 0, 0));
        Vector3f up = paper.worldRot.transform(new Vector3f(0, 1, 0));
        Vector3f normal = paper.worldRot.transform(new Vector3f(0, 0, 1));
        float pw = paper.widthMeters, ph = paper.effectiveHeight();

        float d0 = new Vector3f(base).sub(paper.worldPos).dot(normal);
        float d1 = new Vector3f(tip).sub(paper.worldPos).dot(normal);

        // Does the blade segment cross the plane at all?
        if (Math.abs(d0 - d1) < 1e-6f || (d0 > 0) == (d1 > 0)) {
            penDown = false;
            return;
        }
        float t = d0 / (d0 - d1);
        Vector3f hit = new Vector3f(base).lerp(tip, t);
        Vector3f rel = new Vector3f(hit).sub(paper.worldPos);
        float u = 0.5f + rel.dot(right) / pw;
        float v = 0.5f - rel.dot(up) / ph;

        boolean near = u >= -0.1f && u <= 1.1f && v >= -0.1f && v <= 1.1f;
        if (!near) { penDown = false; return; }

        boolean inBounds = u >= 0 && u <= 1 && v >= 0 && v <= 1;
        float lx = (u - 0.5f) * pw, ly = (0.5f - v) * ph;
        if (trail.size() >= TRAIL_MAX) trail.remove(0);
        trail.add(new TrailPoint(lx, ly, inBounds, penDown));
        penDown = true;

        if (inBounds) {
            cutMinU = Math.min(cutMinU, u); cutMaxU = Math.max(cutMaxU, u);
            cutMinV = Math.min(cutMinV, v); cutMaxV = Math.max(cutMaxV, v);
            if (u <= EDGE_MARGIN) edges[0] = true;
            if (u >= 1 - EDGE_MARGIN) edges[1] = true;
            if (v <= EDGE_MARGIN) edges[2] = true;
            if (v >= 1 - EDGE_MARGIN) edges[3] = true;
            if (edgesReached() >= 2) finalizeCut(right, up, pw, ph);
        }
    }

    /** The stroke spanned two edges: lift the bounding-box region off the menu as a floating piece. */
    private void finalizeCut(Vector3f right, Vector3f up, float pw, float ph) {
        float u0 = cutMinU, v0 = cutMinV, u1 = cutMaxU, v1 = cutMaxV;
        if ((u1 - u0) < MIN_CUT_UV || (v1 - v0) < MIN_CUT_UV) {
            cancelCut();
            return;
        }

        Panel slice = new Panel(u0, v0, u1, v1, PanelAnchor.WORLD);
        slice.widthMeters = pw * (u1 - u0);
        // World position of the slice centre, sitting in the hole it was cut from.
        float cx = (0.5f * (u0 + u1) - 0.5f) * pw;
        float cy = (0.5f - 0.5f * (v0 + v1)) * ph;
        slice.worldPos.set(
                paper.worldPos.x + right.x * cx + up.x * cy,
                paper.worldPos.y + right.y * cx + up.y * cy,
                paper.worldPos.z + right.z * cx + up.z * cy);
        slice.worldRot.set(paper.worldRot);

        PanelManager.add(slice);
        state = State.CUT_READY;
        PanelStore.save();
        DebugLog.logf("CUT", "finalized uv=(%.2f,%.2f)-(%.2f,%.2f) w=%.2f edges=%d",
                u0, v0, u1, v1, slice.widthMeters, edgesReached());
        VrPoses.haptic(true, 0.7f);   // the cut releases
    }

    // --- grab / hold / release ----------------------------------------------

    /** Grab whatever placed panel the off-hand hitbox is reaching into (no physics — a per-frame test). */
    private void tryGrab() {
        VrPoses.BodyPose off = VrPoses.offHand();
        if (off == null) return;
        Vec3 hp = off.pos();
        Vector3f hand = new Vector3f((float) hp.x, (float) hp.y, (float) hp.z);

        Panel nearest = PanelHitbox.nearestTouched(PanelManager.all(), hand, ViveConfig.get().grabRadius);
        if (nearest == null) return;

        nearest.anchorToBody(PanelAnchor.OFF_HAND);   // rides the off hand at a tuned offset
        held = nearest;
        paper = null;                  // the menu disappears while a piece is held
        trail.clear();
        penDown = false;
        state = State.HOLDING;
        DebugLog.logf("GRAB", "hand grabbed panel (dist=%.3f, panels=%d)",
                PanelHitbox.distance(nearest, hand), PanelManager.all().size());
        VrPoses.haptic(false, 0.8f);
    }

    private void doRelease() {
        if (held != null) {
            Panel.Resolved r = held.resolve();
            if (r != null && !glueIfNearBody(held, r)) {
                held.dropToWorld();    // not near a body part → stays in the world
            }
            held = null;
            PanelStore.save();
        }
        enterArmed();                  // re-arm a fresh menu for the next cut
    }

    /** If released near the main hand or head, glue it there (tuned offset) so it follows walking. */
    private boolean glueIfNearBody(Panel p, Panel.Resolved r) {
        float glue = ViveConfig.get().glueRadius;
        VrPoses.BodyPose main = VrPoses.mainHand();
        if (main != null && dist(main.pos(), r.pos()) <= glue) {
            p.anchorToBody(PanelAnchor.MAIN_HAND);
            DebugLog.log("RELEASE", "glued to MAIN_HAND");
            return true;
        }
        VrPoses.BodyPose head = VrPoses.head();
        if (head != null && dist(head.pos(), r.pos()) <= glue) {
            p.anchorToBody(PanelAnchor.HEAD);
            DebugLog.log("RELEASE", "glued to HEAD");
            return true;
        }
        DebugLog.log("RELEASE", "dropped to WORLD");
        return false;
    }

    private static float dist(Vec3 a, Vector3f b) {
        float dx = (float) a.x - b.x, dy = (float) a.y - b.y, dz = (float) a.z - b.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // --- mode enter / exit ---------------------------------------------------

    private void enterArmed() {
        VrPoses.BodyPose head = VrPoses.head();
        if (head == null) return;

        Vec3 hp = head.pos();
        Vec3 hd = head.dir();
        paper = new Panel(0f, 0f, 1f, 1f, PanelAnchor.WORLD);
        ViveConfig cfg = ViveConfig.get();
        paper.widthMeters = cfg.paperWidth;
        paper.worldPos.set(
                (float) (hp.x + hd.x * cfg.paperDistance),
                (float) (hp.y + hd.y * cfg.paperDistance),
                (float) (hp.z + hd.z * cfg.paperDistance));
        paper.worldRot.set(head.rot());

        state = State.ARMED;
        resetStroke();
    }

    private void exit() {
        if (held != null) { held.dropToWorld(); held = null; PanelStore.save(); }
        paper = null;                  // any un-grabbed floating piece stays placed in the world
        state = State.OFF;
        resetStroke();
    }

    private void resetStroke() {
        trail.clear();
        penDown = false;
        edges[0] = edges[1] = edges[2] = edges[3] = false;
        cutMinU = 1f; cutMinV = 1f; cutMaxU = 0f; cutMaxV = 0f;
    }

    private int edgesReached() {
        int n = 0;
        for (boolean e : edges) if (e) n++;
        return n;
    }
}
