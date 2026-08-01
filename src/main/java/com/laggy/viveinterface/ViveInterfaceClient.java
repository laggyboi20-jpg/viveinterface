package com.laggy.viveinterface;

import com.laggy.viveinterface.config.ViveConfig;
import com.laggy.viveinterface.cut.CutTool;
import com.laggy.viveinterface.debug.DebugLog;
import com.laggy.viveinterface.debug.GameLogTap;
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
        ViveConfig.load();   // restore the debug-logging toggles
        DebugLog.logEnvironment();   // what was running, for shared logs
        GameLogTap.install();        // mirror Vivecraft/Minecraft output into our file
        PanelStore.load();   // bring back panels cut in previous sessions

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // N opens the flat cut screen (Vivecraft shows it as the pointer panel). Placing pieces
            // on your body no longer needs a mode — see CutTool.
            while (ViveKeys.toggleCut.consumeClick()) {
                Minecraft.getInstance().setScreen(new CutScreen());
            }
            CutTool.get().tick();
        });

        DebugLog.log("INIT", "ready — N opens the cut screen (drag a box on the HUD to cut). "
                + "Bind a grab key to a grip to grab pieces. All settings: Mod Menu → ViveInterface.");
    }
}
