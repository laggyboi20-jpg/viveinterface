package com.laggy.viveinterface.cut;

/**
 * Decides which vanilla key bindings are neutralised while cut mode is active. Vivecraft maps VR
 * controller buttons onto vanilla {@code KeyMapping}s, and it exposes no input API — so the only way
 * to make every other VR button obsolete during cutting is to suppress those mappings. That stops
 * the game acting on them (no block-breaking, item use, inventory, hotbar, movement) so the whole
 * controller is dedicated to cutting.
 *
 * <p>Cut mode is fully modal: everything is suppressed except ViveInterface's own keys and
 * Vivecraft's own bindings (kept so the VR menu / turning still work). The cut/release triggers are
 * read raw via {@link com.laggy.viveinterface.vr.VrTriggers}, so suppressing ATTACK/USE here is fine.
 */
public final class CutInputGate {

    private CutInputGate() {}

    /** True if the named binding should be treated as "not pressed" right now. */
    public static boolean suppress(String keyName) {
        if (keyName == null) return false;
        if (keyName.startsWith("key.viveinterface.")) return false;   // always leave our own keys

        // Placement mode is fully modal: suppress EVERYTHING else, Vivecraft's own bindings included,
        // because that's what the trigger teleport / walk-forward runs through. Trigger state is still
        // read raw (KeyMappingAccessor), so grabbing keeps working while the game sees nothing.
        if (PlacementMode.active()) return true;

        // Otherwise only while actually carrying a piece, so a grab doesn't also mine or place.
        if (!CutTool.get().active()) return false;
        if (keyName.toLowerCase().contains("vivecraft")) return false; // leave Vivecraft's bindings
        return true;
    }
}
