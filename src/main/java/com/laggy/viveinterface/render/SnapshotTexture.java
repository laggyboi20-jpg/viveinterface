package com.laggy.viveinterface.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * Publishes {@link GuiSnapshot}'s GPU texture to the {@code TextureManager} under a normal
 * {@link Identifier}.
 *
 * <p>Why: world drawing in 26.2 goes through a {@code RenderType}, and the vanilla factories
 * ({@code RenderTypes.entityTranslucent(Identifier)}) take a registered texture id rather than a raw
 * {@code GpuTextureView}. Registering a thin {@link AbstractTexture} that simply points at the
 * snapshot lets the panels use stock render types instead of a hand-written {@code RenderPipeline}.
 *
 * <p>The fields are re-pointed every frame because {@link GuiSnapshot} recreates its texture whenever
 * the HUD resolution changes.
 */
public final class SnapshotTexture extends AbstractTexture {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("viveinterface", "hud_snapshot");

    private static SnapshotTexture instance;

    private SnapshotTexture() {}

    /** Point the registered texture at the current snapshot. Returns false if there's nothing to show. */
    public static boolean sync() {
        if (!GuiSnapshot.ready()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getTextureManager() == null) return false;

        if (instance == null) {
            instance = new SnapshotTexture();
            mc.getTextureManager().register(ID, instance);
        }
        instance.texture = GuiSnapshot.texture();
        instance.textureView = GuiSnapshot.view();
        instance.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        return instance.textureView != null;
    }

    /** The snapshot is owned by {@link GuiSnapshot}; don't let the texture manager free it. */
    @Override
    public void close() {
        texture = null;
        textureView = null;
    }
}
