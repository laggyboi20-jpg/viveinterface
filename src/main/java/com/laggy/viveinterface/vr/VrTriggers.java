package com.laggy.viveinterface.vr;

import com.laggy.viveinterface.config.ViveConfig;
import com.laggy.viveinterface.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Reads the VR controller triggers. Vivecraft maps them onto the vanilla ATTACK / USE key bindings,
 * so we read those — via {@link KeyMappingAccessor} so our own input gate doesn't hide them.
 *
 * <p>Assumes the dominant (sword) hand trigger = ATTACK and the off hand trigger = USE. If your
 * Vivecraft bindings are swapped, flip the two keys below.
 */
public final class VrTriggers {

    private VrTriggers() {}

    /** Dominant/right-hand trigger — starts and drives a cut (ATTACK, or USE if swapped). */
    public static boolean cut() {
        var opt = Minecraft.getInstance().options;
        return raw(ViveConfig.get().swapTriggers ? opt.keyUse : opt.keyAttack);
    }

    /** Off/left-hand trigger — lets go of the held slice (USE, or ATTACK if swapped). */
    public static boolean release() {
        var opt = Minecraft.getInstance().options;
        return raw(ViveConfig.get().swapTriggers ? opt.keyAttack : opt.keyUse);
    }

    private static boolean raw(KeyMapping k) {
        if (k == null) return false;
        return ((KeyMappingAccessor) (Object) k).viveinterface$isDownRaw();
    }
}
