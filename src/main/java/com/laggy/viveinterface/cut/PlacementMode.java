package com.laggy.viveinterface.cut;

import com.laggy.viveinterface.debug.DebugLog;
import com.laggy.viveinterface.vr.VrPoses;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Placement mode: an <b>in-world</b> modal state for putting cut pieces onto your body.
 *
 * <p>This deliberately is <i>not</i> a {@link net.minecraft.client.gui.screens.Screen}. While a screen
 * is open, Vivecraft routes the controller triggers to the GUI pointer instead of the key bindings, so
 * {@link com.laggy.viveinterface.vr.VrTriggers} sees nothing and grabbing is impossible — and the VR
 * hands don't render properly either. Instead we stay in the world (hands and pieces render normally,
 * triggers still reach us) and neutralise movement ourselves:
 *
 * <ul>
 *   <li>{@link CutInputGate} suppresses every binding that isn't ours, so the trigger can't teleport,
 *       walk, mine or place. Trigger state is still read raw, so grabbing keeps working.</li>
 *   <li>{@link #tick()} additionally zeroes the player's movement input each tick, which stops walking
 *       even if something drives it without going through a key binding.</li>
 * </ul>
 *
 * <p>Entered from the cut screen's "Done" button; left by pressing the mod's keybind (N) again.
 */
public final class PlacementMode {

    private static boolean active;

    private PlacementMode() {}

    public static boolean active() { return active; }

    public static void enter() {
        if (active) return;
        active = true;
        DebugLog.log("PLACE", "placement mode ON — movement locked, grab pieces onto your body");
    }

    public static void exit() {
        if (!active) return;
        active = false;
        DebugLog.log("PLACE", "placement mode OFF");
    }

    public static void toggle() {
        if (active) exit(); else enter();
    }

    /** Half-size of the in-world Done button, metres. */
    public static final float DONE_HALF = 0.07f;

    /**
     * World position of the in-world <b>Done</b> button: a box floating below and in front of your
     * head that you exit placement mode by touching with a hand and squeezing the trigger.
     *
     * <p>This exists because Minecraft key bindings can't reach the VR face buttons — Vivecraft
     * translates controller buttons into SteamVR input actions from its own manifest, and a modded
     * binding isn't in the default profile, so "press N to finish" is useless in a headset. A button
     * you physically touch needs no binding at all: hand poses and trigger state are things the mod
     * already reads reliably.
     *
     * <p>It follows the head (kept in reach) rather than being pinned in the world, so turning around
     * never leaves it behind. Null if there's no head pose.
     */
    public static Vector3f donePos() {
        VrPoses.BodyPose head = VrPoses.head();
        if (head == null) return null;
        Vec3 p = head.pos();
        Vec3 d = head.dir();
        // Flatten the look direction so the button doesn't fly up/down as you tilt your head.
        float fx = (float) d.x, fz = (float) d.z;
        float len = (float) Math.sqrt(fx * fx + fz * fz);
        if (len < 1e-4f) { fx = 0f; fz = 1f; len = 1f; }
        fx /= len; fz /= len;
        return new Vector3f(
                (float) p.x + fx * 0.42f,
                (float) p.y - 0.38f,          // below eye level, roughly chest height
                (float) p.z + fz * 0.42f);
    }

    /** Hold the player still while placing. Called every client tick. */
    public static void tick() {
        if (!active) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            exit();          // left the world while in placement mode
            return;
        }
        // 26.2 dropped the impulse fields; zeroing the key presses is what drives movement now.
        player.input.keyPresses = Input.EMPTY;
    }
}
