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
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Placing and handling cut pieces.
 *
 * <p><b>Cutting</b> happens on the flat {@link com.laggy.viveinterface.gui.CutScreen} (drag a box,
 * press Cut → {@link #placeFromUv}). This class owns what happens to a piece afterwards, and it all
 * works during normal play — no mode to enter:
 *
 * <ul>
 *   <li>Reach a hand into a placed piece — it tints <b>green</b> (grabbable).</li>
 *   <li>Squeeze that hand's trigger to <b>grab</b> it; the piece keeps its exact orientation and
 *       rides the hand.</li>
 *   <li>Let go and it stays exactly where you released it. Let go <b>touching your other hand or your
 *       head</b> and it sticks there, following you as you walk.</li>
 *   <li>Reach across with the <b>other</b> hand to take a stuck piece back off and drop it anywhere.
 *       (A piece never responds to the hand it's sitting on — otherwise every trigger squeeze would
 *       re-grab it.)</li>
 * </ul>
 *
 * <p>{@link PlacementMode} is now optional: it locks movement and shows the anchor volumes, which is
 * handy for careful placement, but the same gestures work without it.
 */
public final class CutTool {

    public enum State { OFF, HOLDING }

    private static final float MIN_CUT_UV = 0.01f;    // ignore degenerate slivers

    private static final CutTool INSTANCE = new CutTool();
    public static CutTool get() { return INSTANCE; }

    private Panel held;                 // the piece currently riding a hand
    private PanelAnchor heldHand;       // which hand holds it
    private boolean heldByMainTrigger;  // which trigger started the grab (released on that one)
    private boolean prevMain, prevOff;  // trigger edge detection

    public State state() { return held != null ? State.HOLDING : State.OFF; }
    /** True while a piece is being carried — suppresses vanilla bindings so you don't mine/punch. */
    public boolean active() { return held != null; }
    public Panel heldPanel() { return held; }

    /** True while placement mode is on: movement is locked and body-sticking is enabled. */
    public static boolean placementMode() {
        return PlacementMode.active();
    }

    /**
     * The placed panel a hand is reaching into — drives the green tint. Shown whenever a piece is
     * actually grabbable, which (see {@link #nearestTo}) never includes a piece resting on the very
     * hand doing the reaching, so a piece stuck to your hand doesn't sit there glowing as you play.
     */
    public Panel touchedPanel() {
        if (!VrPoses.vrActive()) return null;
        if (held != null) return held;
        Panel p = nearestTo(VrPoses.mainHand(), PanelAnchor.MAIN_HAND);
        return p != null ? p : nearestTo(VrPoses.offHand(), PanelAnchor.OFF_HAND);
    }

    /**
     * Nearest grabbable piece for one hand.
     *
     * <p>A piece anchored to <i>this</i> hand is skipped: it sits exactly at the hand, so it would be
     * re-grabbed on every trigger squeeze (and grabbing suppresses mining). Reach across with the other
     * hand to take it off — which is also the natural gesture for it.
     */
    private static Panel nearestTo(VrPoses.BodyPose hand, PanelAnchor handAnchor) {
        if (hand == null) return null;
        Vec3 hp = hand.pos();
        List<Panel> candidates = PanelManager.all().stream()
                .filter(p -> p.anchor != handAnchor)
                .toList();
        return PanelHitbox.nearestTouched(candidates,
                new Vector3f((float) hp.x, (float) hp.y, (float) hp.z), ViveConfig.get().grabRadius);
    }

    /**
     * Lift a rectangular UV region of the HUD out into a floating world panel, positioned in front of
     * the viewer. Called by {@link com.laggy.viveinterface.gui.CutScreen} when you drag a box on the
     * flat cut screen and press "Cut". UVs are top-left origin in [0,1]. Returns false if the box is
     * too small.
     */
    public static boolean placeFromUv(float u0, float v0, float u1, float v1) {
        float minU = Math.max(0f, Math.min(u0, u1)), maxU = Math.min(1f, Math.max(u0, u1));
        float minV = Math.max(0f, Math.min(v0, v1)), maxV = Math.min(1f, Math.max(v0, v1));
        if ((maxU - minU) < MIN_CUT_UV || (maxV - minV) < MIN_CUT_UV) return false;

        ViveConfig cfg = ViveConfig.get();
        Panel slice = new Panel(minU, minV, maxU, maxV, PanelAnchor.WORLD);
        slice.widthMeters = Math.max(0.05f, cfg.paperWidth * (maxU - minU));

        // Place it a fixed distance in front of the head (VR) or camera (desktop), facing the viewer.
        VrPoses.BodyPose head = VrPoses.head();
        if (head != null) {
            Vec3 p = head.pos(), d = head.dir();
            slice.worldPos.set(
                    (float) (p.x + d.x * cfg.paperDistance),
                    (float) (p.y + d.y * cfg.paperDistance),
                    (float) (p.z + d.z * cfg.paperDistance));
            slice.worldRot.set(head.rot());
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return false;
            Vec3 eye = mc.player.getEyePosition(1f);
            Vec3 look = mc.player.getViewVector(1f);
            slice.worldPos.set(
                    (float) (eye.x + look.x * cfg.paperDistance),
                    (float) (eye.y + look.y * cfg.paperDistance),
                    (float) (eye.z + look.z * cfg.paperDistance));
            slice.worldRot.set(new Quaternionf().rotationTo(
                    0f, 0f, 1f, (float) look.x, (float) look.y, (float) look.z));
        }

        // Stagger multiple pieces sideways (along the panel's right axis) so a second cut doesn't land
        // exactly on the first — coplanar quads at the same spot z-fight and look like flickering.
        int existing = PanelManager.all().size();
        if (existing > 0) {
            Vector3f right = slice.worldRot.transform(new Vector3f(1f, 0f, 0f));
            float step = slice.widthMeters + 0.04f;
            slice.worldPos.add(right.mul(existing * step));
        }

        PanelManager.add(slice);
        PanelStore.save();
        DebugLog.logf("CUT", "screen cut uv=(%.2f,%.2f)-(%.2f,%.2f) w=%.2f panels=%d",
                minU, minV, maxU, maxV, slice.widthMeters, PanelManager.all().size());
        VrPoses.haptic(true, 0.6f);
        return true;
    }

    // --- per-frame VR grab handling ------------------------------------------

    public void tick() {
        if (!VrPoses.vrActive()) return;
        // Any open screen owns the triggers for its pointer, so stay out of the way. Placement mode is
        // deliberately screen-less precisely so grabbing keeps working there.
        if (Minecraft.getInstance().screen != null) {
            prevMain = prevOff = false;
            return;
        }

        boolean mainDown = VrTriggers.cut();       // dominant-hand trigger
        boolean offDown = VrTriggers.release();    // off-hand trigger

        if (held != null) {
            boolean stillHeld = heldByMainTrigger ? mainDown : offDown;
            if (!stillHeld) doRelease();
        } else {
            // Touching the in-world Done button and squeezing exits placement mode — the only exit
            // that needs no key binding, since VR face buttons never reach Minecraft key bindings.
            if (placementMode() && (mainDown && !prevMain || offDown && !prevOff) && touchingDone()) {
                DebugLog.log("PLACE", "Done button pressed");
                VrPoses.haptic(true, 0.9f);
                PlacementMode.exit();
            } else if (mainDown && !prevMain) tryGrab(true);
            else if (offDown && !prevOff) tryGrab(false);
        }

        prevMain = mainDown;
        prevOff = offDown;
    }

    /**
     * Grab a piece on a trigger press. Prefers the hand that trigger belongs to, but falls back to the
     * other hand if only that one is touching a piece — Vivecraft's trigger→binding mapping varies
     * between setups, so either trigger can grab whichever hand is actually in range.
     */
    private void tryGrab(boolean mainTrigger) {
        PanelAnchor first = mainTrigger ? PanelAnchor.MAIN_HAND : PanelAnchor.OFF_HAND;
        PanelAnchor second = mainTrigger ? PanelAnchor.OFF_HAND : PanelAnchor.MAIN_HAND;

        PanelAnchor hand = first;
        VrPoses.BodyPose pose = poseOf(first);
        Panel target = nearestTo(pose, first);
        if (target == null) {
            hand = second;
            pose = poseOf(second);
            target = nearestTo(pose, second);
        }
        if (target == null || pose == null) return;

        target.attachToBody(hand, pose);
        held = target;
        heldHand = hand;
        heldByMainTrigger = mainTrigger;
        DebugLog.logf("GRAB", "%s grabbed a piece (placementMode=%s panels=%d)",
                hand, placementMode(), PanelManager.all().size());
        VrPoses.haptic(hand == PanelAnchor.MAIN_HAND, 0.8f);
    }

    private static VrPoses.BodyPose poseOf(PanelAnchor hand) {
        return hand == PanelAnchor.MAIN_HAND ? VrPoses.mainHand() : VrPoses.offHand();
    }

    /** Is either hand inside the in-world Done button's box? */
    private static boolean touchingDone() {
        Vector3f c = PlacementMode.donePos();
        if (c == null) return false;
        float reach = PlacementMode.DONE_HALF + ViveConfig.get().grabRadius;
        return near(VrPoses.mainHand(), c, reach) || near(VrPoses.offHand(), c, reach);
    }

    private static boolean near(VrPoses.BodyPose hand, Vector3f c, float reach) {
        if (hand == null) return false;
        Vec3 p = hand.pos();
        return Math.abs(p.x - c.x) <= reach && Math.abs(p.y - c.y) <= reach && Math.abs(p.z - c.z) <= reach;
    }

    /** Let go: stick to a nearby body part if you released it there, else leave it in the world. */
    private void doRelease() {
        Panel p = held;
        held = null;
        if (p == null) return;

        // Sticking works during normal play, not just in placement mode: carry a piece to your other
        // hand (or head) and let go and it sticks there; take it off again with the other hand and drop
        // it wherever you like. Placement mode is now just a calmer way to do the same thing.
        Panel.Resolved r = p.resolve();
        if (r != null && glueIfNearBody(p, r)) {
            PanelStore.save();
            return;
        }
        p.dropToWorld();               // stays exactly where it was released
        PanelStore.save();
        DebugLog.log("RELEASE", "dropped to WORLD");
        VrPoses.haptic(heldHand == PanelAnchor.MAIN_HAND, 0.4f);
    }

    /**
     * If released touching the <b>other</b> hand or the head, stick it there (it then follows that body
     * part as you move) instead of leaving it floating in the world.
     */
    private boolean glueIfNearBody(Panel p, Panel.Resolved r) {
        float glue = ViveConfig.get().glueRadius;
        PanelAnchor other = (heldHand == PanelAnchor.MAIN_HAND) ? PanelAnchor.OFF_HAND : PanelAnchor.MAIN_HAND;
        VrPoses.BodyPose otherHand = (other == PanelAnchor.MAIN_HAND) ? VrPoses.mainHand() : VrPoses.offHand();
        if (otherHand != null && dist(otherHand.pos(), r.pos()) <= glue) {
            p.attachToBody(other, otherHand);
            DebugLog.logf("RELEASE", "stuck to %s", other);
            VrPoses.haptic(other == PanelAnchor.MAIN_HAND, 0.9f);
            return true;
        }
        VrPoses.BodyPose head = VrPoses.head();
        if (head != null && dist(head.pos(), r.pos()) <= glue) {
            p.attachToBody(PanelAnchor.HEAD, head);
            DebugLog.log("RELEASE", "stuck to HEAD");
            VrPoses.haptic(true, 0.9f);
            return true;
        }
        return false;
    }

    private static float dist(Vec3 a, Vector3f b) {
        float dx = (float) a.x - b.x, dy = (float) a.y - b.y, dz = (float) a.z - b.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
