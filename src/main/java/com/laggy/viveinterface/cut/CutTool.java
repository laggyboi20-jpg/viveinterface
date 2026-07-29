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
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Placing and handling cut pieces.
 *
 * <p><b>Cutting</b> happens on the flat {@link com.laggy.viveinterface.gui.CutScreen} (drag a box,
 * press Cut → {@link #placeFromUv}). This class owns what happens to a piece afterwards, in VR:
 *
 * <ul>
 *   <li>Reach a hand into a placed piece — it tints <b>green</b> (grabbable).</li>
 *   <li>Squeeze that hand's trigger to <b>grab</b> it; the piece keeps its exact orientation and
 *       rides the hand (right trigger = right/main hand, left trigger = left/off hand).</li>
 *   <li>Let go of the trigger to <b>drop</b> it: it stays exactly where you released it. Release it
 *       close to your <b>other hand or head</b> and it sticks to that body part instead, following you
 *       as you walk.</li>
 * </ul>
 */
public final class CutTool {

    public enum State { OFF, HOLDING }

    private static final float MIN_CUT_UV = 0.01f;    // ignore degenerate slivers

    private static final CutTool INSTANCE = new CutTool();
    public static CutTool get() { return INSTANCE; }

    private Panel held;                 // the piece currently riding a hand
    private PanelAnchor heldHand;       // which hand holds it
    private boolean prevMain, prevOff;  // trigger edge detection

    public State state() { return held != null ? State.HOLDING : State.OFF; }
    /** True while a piece is being carried — suppresses vanilla bindings so you don't mine/punch. */
    public boolean active() { return held != null; }
    public Panel heldPanel() { return held; }

    /** The placed panel a hand is currently reaching into (grabbable) — drives the green tint. */
    public Panel touchedPanel() {
        if (held != null) return held;
        if (!VrPoses.vrActive()) return null;
        Panel p = nearestTo(VrPoses.mainHand());
        return p != null ? p : nearestTo(VrPoses.offHand());
    }

    private static Panel nearestTo(VrPoses.BodyPose hand) {
        if (hand == null) return null;
        Vec3 hp = hand.pos();
        return PanelHitbox.nearestTouched(PanelManager.all(),
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
        // Don't grab while a menu (including the cut screen) is open — the triggers drive the pointer.
        if (Minecraft.getInstance().screen != null) {
            prevMain = prevOff = false;
            return;
        }

        boolean mainDown = VrTriggers.cut();       // dominant-hand trigger
        boolean offDown = VrTriggers.release();    // off-hand trigger

        if (held != null) {
            boolean stillHeld = (heldHand == PanelAnchor.MAIN_HAND) ? mainDown : offDown;
            if (!stillHeld) doRelease();
        } else {
            if (mainDown && !prevMain) tryGrab(PanelAnchor.MAIN_HAND, VrPoses.mainHand());
            else if (offDown && !prevOff) tryGrab(PanelAnchor.OFF_HAND, VrPoses.offHand());
        }

        prevMain = mainDown;
        prevOff = offDown;
    }

    /** Grab whatever piece this hand is reaching into, keeping its current position/orientation. */
    private void tryGrab(PanelAnchor hand, VrPoses.BodyPose pose) {
        Panel target = nearestTo(pose);
        if (target == null) return;
        target.attachToBody(hand, pose);
        held = target;
        heldHand = hand;
        DebugLog.logf("GRAB", "%s grabbed a piece (panels=%d)", hand, PanelManager.all().size());
        VrPoses.haptic(hand == PanelAnchor.MAIN_HAND, 0.8f);
    }

    /** Let go: stick to a nearby body part if you released it there, else leave it in the world. */
    private void doRelease() {
        Panel p = held;
        held = null;
        if (p == null) return;

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
