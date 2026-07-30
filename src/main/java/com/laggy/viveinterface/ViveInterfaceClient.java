package com.laggy.viveinterface;

import com.laggy.viveinterface.config.ViveConfig;
import com.laggy.viveinterface.cut.CutTool;
import com.laggy.viveinterface.cut.PlacementMode;
import com.laggy.viveinterface.debug.DebugLog;
import com.laggy.viveinterface.gui.CutScreen;
import com.laggy.viveinterface.panel.PanelStore;
import com.laggy.viveinterface.render.HudMask;
import com.laggy.viveinterface.render.PanelRenderer;
import com.laggy.viveinterface.render.PlacementHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public class ViveInterfaceClient implements ClientModInitializer {

    public static final String MOD_ID = "viveinterface";

    @Override
    public void onInitializeClient() {
        // ViveInterface is only meaningful alongside Vivecraft. Without it, register nothing so we
        // never classload the Vivecraft-referencing code (VrPoses / GuiTexture / renderer).
        if (!FabricLoader.getInstance().isModLoaded("vivecraft")) {
            System.out.println("[ViveInterface] Vivecraft not present — mod idle.");
            return;
        }

        ViveKeys.register();

        PanelRenderer.register();
        HudMask.register();       // snapshot the HUD + hole out cut regions from the flat panel
        PlacementHud.register();  // AFTER HudMask, so its text isn't captured into cut pieces
        ViveConfig.load();   // restore the debug-logging toggle
        PanelStore.load();   // bring back panels cut in previous sessions

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // N opens the flat cut screen (Vivecraft shows it as the pointer panel), or leaves
            // placement mode if that's running — so the one key always gets you back out.
            while (ViveKeys.toggleCut.consumeClick()) {
                if (PlacementMode.active()) PlacementMode.exit();
                else Minecraft.getInstance().setScreen(new CutScreen());
            }
            // Bind this to a controller face button to leave placement mode without a keyboard.
            while (ViveKeys.exitPlacement.consumeClick()) PlacementMode.exit();
            PlacementMode.tick();   // hold the player still while placing
            CutTool.get().tick();
        });

        DebugLog.log("INIT", "ready — N opens the cut screen (drag a box on the HUD to cut), "
                + "and leaves placement mode. All settings: Mod Menu → ViveInterface.");
    }
}
