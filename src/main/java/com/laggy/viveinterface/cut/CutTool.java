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
     * The placed panel a hand is reaching into — drives the green tint. Only highlighted in placement
     * mode: outside it, a piece riding your hand shouldn't glow while you're just playing.
     */
    public Panel touchedPanel() {
        if (!VrPoses.vrActive() || !placementMode()) return null;
        if (held != null) return held;
        Panel p = nearestTo(VrPoses.mainHand());
        return p != null ? p : nearestTo(VrPoses.offHand());
    }

    private static Panel nearestTo(VrPoses.BodyPose hand) {
        if (hand == null) return null;
        Vec3 hp = hand.pos();
        // Outside placement mode only WORLD pieces are grabbable. A piece stuck to a hand sits right at
        // that hand, so it would otherwise be re-grabbed every time you squeezed the trigger to mine.
        List<Panel> candidates = PanelManager.all();
        if (!placementMode()) {
            candidates = candidates.stream().filter(p -> p.anchor == PanelAnchor.WORLD).toList();
        }
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
            if (mainDown && !prevMain) tryGrab(true);
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
        Panel target = nearestTo(pose);
        if (target == null) {
            hand = second;
            pose = poseOf(second);
            target = nearestTo(pose);
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

    /** Let go: stick to a nearby body part if you released it there, else leave it in the world. */
    private void doRelease() {
        Panel p = held;
        held = null;
        if (p == null) return;

        // Sticking to a body part only happens in placement mode. Outside it, grabbing is purely for
        // nudging a piece around the world, so a release always leaves it in the world.
        Panel.Resolved r = p.resolve();
        if (placementMode() && r != null && glueIfNearBody(p, r)) {
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
