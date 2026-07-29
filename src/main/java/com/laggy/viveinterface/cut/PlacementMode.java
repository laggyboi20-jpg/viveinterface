package com.laggy.viveinterface.cut;

import com.laggy.viveinterface.debug.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;

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

    /** Hold the player still while placing. Called every client tick. */
    public static void tick() {
        if (!active) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            exit();          // left the world while in placement mode
            return;
        }
        player.input.forwardImpulse = 0f;
        player.input.leftImpulse = 0f;
        player.input.keyPresses = new Input(false, false, false, false, false, false, false);
    }
}
