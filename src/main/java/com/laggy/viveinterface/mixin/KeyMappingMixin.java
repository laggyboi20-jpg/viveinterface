package com.laggy.viveinterface.mixin;

import com.laggy.viveinterface.cut.CutInputGate;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Neutralises vanilla key bindings while ViveInterface cut mode is active (see {@link CutInputGate}).
 * {@code isDown} blocks held actions (attack/use); {@code consumeClick} blocks discrete ones
 * (inventory, hotbar, drop) and drains the queued click count so nothing fires when cut mode ends.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {

    @Shadow private int clickCount;

    @Shadow public abstract String getName();

    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
    private void viveinterface$gateDown(CallbackInfoReturnable<Boolean> cir) {
        if (CutInputGate.suppress(getName())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "consumeClick", at = @At("HEAD"), cancellable = true)
    private void viveinterface$gateClick(CallbackInfoReturnable<Boolean> cir) {
        if (CutInputGate.suppress(getName())) {
            this.clickCount = 0;         // discard queued presses so they don't fire on exit
            cir.setReturnValue(false);
        }
    }
}
