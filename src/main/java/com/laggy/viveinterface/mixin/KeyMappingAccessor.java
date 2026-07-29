package com.laggy.viveinterface.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the raw {@code isDown} field of a {@link KeyMapping}, bypassing our own
 * {@link KeyMappingMixin} gate (which forces the {@code isDown()} getter to false during cut mode).
 * Vivecraft still writes the field when a controller trigger is pressed, so this gives us the real
 * trigger state to drive cutting while the game itself sees the button as inert.
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("isDown")
    boolean viveinterface$isDownRaw();
}
