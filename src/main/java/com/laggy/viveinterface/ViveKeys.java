package com.laggy.viveinterface;

import com.laggy.viveinterface.mixin.KeyMappingAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * The mod's key bindings.
 *
 * <p>These exist mainly as a way around a VR input problem: Vivecraft owns the controller triggers
 * (the left one drives teleport / walk-forward), so we can't read vanilla ATTACK/USE for the off hand.
 * A binding of <i>ours</i>, on the other hand, can be pointed at any controller button — X, Y, a grip,
 * whatever — from Vivecraft's own controls screen, and {@link com.laggy.viveinterface.cut.CutInputGate}
 * never suppresses our keys, so they keep working even while placement mode blocks everything else.
 *
 * <p>Only {@code N} has a default; the VR-oriented ones start unbound on purpose, so they don't collide
 * with an existing binding before you've chosen a button for them.
 */
public final class ViveKeys {

    private static final String CATEGORY = "category.viveinterface";

    /** Open the cut screen; also leaves placement mode. Default N. */
    public static KeyMapping toggleCut;
    /** Grab with the MAIN hand — bind to a right-controller grip. Unbound. */
    public static KeyMapping grabMainHand;
    /** Grab with the OFF hand — bind to a left-controller grip. Unbound. */
    public static KeyMapping grabOffHand;
    /** Leave placement mode without reaching for the keyboard — bind to any face button. Unbound. */
    public static KeyMapping exitPlacement;

    private ViveKeys() {}

    public static void register() {
        toggleCut = reg("key.viveinterface.toggle_cut", GLFW.GLFW_KEY_N);
        grabMainHand = reg("key.viveinterface.grab_main", InputConstants.UNKNOWN.getValue());
        grabOffHand = reg("key.viveinterface.grab_off", InputConstants.UNKNOWN.getValue());
        exitPlacement = reg("key.viveinterface.exit_placement", InputConstants.UNKNOWN.getValue());
    }

    /**
     * True once either grab key has been bound. Then the triggers are left alone entirely, so
     * ATTACK stays free for mining — squeezing to break a block can't be swallowed by a grab.
     */
    public static boolean dedicatedGrabKeys() {
        return isBound(grabMainHand) || isBound(grabOffHand);
    }

    private static KeyMapping reg(String id, int key) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyMapping(id, InputConstants.Type.KEYSYM, key, CATEGORY));
    }

    /** Is this binding actually bound to something? */
    public static boolean isBound(KeyMapping k) {
        return k != null && !k.isUnbound();
    }

    /**
     * Raw held state, read straight off the key field so it still reports correctly while
     * {@link com.laggy.viveinterface.cut.CutInputGate} is making the getter lie to the game.
     */
    public static boolean rawDown(KeyMapping k) {
        if (!isBound(k)) return false;
        return ((KeyMappingAccessor) (Object) k).viveinterface$isDownRaw();
    }
}
