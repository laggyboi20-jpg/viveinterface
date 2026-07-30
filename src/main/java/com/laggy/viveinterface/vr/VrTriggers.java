package com.laggy.viveinterface.vr;

import com.laggy.viveinterface.config.ViveConfig;
import com.laggy.viveinterface.ViveKeys;
import com.laggy.viveinterface.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Reads the VR controller triggers. Vivecraft maps them onto the vanilla ATTACK / USE key bindings, so
 * we read those — via {@link KeyMappingAccessor} so our own input gate doesn't hide them.
 *
 * <p>Assumes the dominant hand trigger = ATTACK and the off hand trigger = USE; the "swap triggers"
 * setting flips them. Prefer binding a dedicated grab key ({@link ViveKeys}) — see {@link #release()}.
 */
public final class VrTriggers {

    private VrTriggers() {}

    /** Dominant/right-hand trigger — starts and drives a cut (ATTACK, or USE if swapped). */
    public static boolean cut() {
        var opt = Minecraft.getInstance().options;
        return raw(ViveConfig.get().swapTriggers ? opt.keyUse : opt.keyAttack);
    }

    /**
     * Off-hand grab button.
     *
     * <p>Vivecraft binds the left controller trigger to teleport / walk-forward, so vanilla USE never
     * actually goes down for the off hand — which is why off-hand grabbing never fired. If the mod's
     * own {@link ViveKeys#grabOffHand} binding has been pointed at a left-controller button (X / Y /
     * grip) in Vivecraft's controls, use that; otherwise fall back to the vanilla USE key so desktop
     * and any setup that does map it still work.
     */
    public static boolean release() {
        if (ViveKeys.isBound(ViveKeys.grabOffHand)) return ViveKeys.rawDown(ViveKeys.grabOffHand);
        var opt = Minecraft.getInstance().options;
        return raw(ViveConfig.get().swapTriggers ? opt.keyAttack : opt.keyUse);
    }

    private static boolean raw(KeyMapping k) {
        if (k == null) return false;
        return ((KeyMappingAccessor) (Object) k).viveinterface$isDownRaw();
    }
}
