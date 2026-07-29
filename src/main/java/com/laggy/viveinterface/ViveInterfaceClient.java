package com.laggy.viveinterface;

import com.laggy.viveinterface.config.ViveConfig;
import com.laggy.viveinterface.cut.CutTool;
import com.laggy.viveinterface.debug.DebugLog;
import com.laggy.viveinterface.gui.ViveInterfaceScreen;
import com.laggy.viveinterface.panel.PanelStore;
import com.laggy.viveinterface.render.HudMask;
import com.laggy.viveinterface.render.PanelRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class ViveInterfaceClient implements ClientModInitializer {

    public static final String MOD_ID = "viveinterface";

    private static KeyMapping keyToggleCut;
    private static KeyMapping keyPlace;
    private static KeyMapping keyChangeHand;
    private static KeyMapping keySettings;
    private static KeyMapping keyDebugDump;
    private static KeyMapping keyDebugToggle;

    @Override
    public void onInitializeClient() {
        // ViveInterface is only meaningful alongside Vivecraft. Without it, register nothing so we
        // never classload the Vivecraft-referencing code (VrPoses / GuiTexture / renderer).
        if (!FabricLoader.getInstance().isModLoaded("vivecraft")) {
            System.out.println("[ViveInterface] Vivecraft not present — mod idle.");
            return;
        }

        keyToggleCut = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.viveinterface.toggle_cut",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N,
                "category.viveinterface"));
        keyPlace = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.viveinterface.place",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M,
                "category.viveinterface"));
        keyChangeHand = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.viveinterface.change_hand",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G,   // bind your Quest A button to this
                "category.viveinterface"));
        keySettings = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.viveinterface.settings",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K,
                "category.viveinterface"));
        keyDebugDump = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.viveinterface.debug_dump",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J,
                "category.viveinterface"));
        keyDebugToggle = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.viveinterface.debug_toggle",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L,
                "category.viveinterface"));

        PanelRenderer.register();
        HudMask.register();  // snapshot the HUD + hole out cut regions from the flat panel
        ViveConfig.load();   // restore the debug-logging toggle
        PanelStore.load();   // bring back panels cut in previous sessions

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (keyToggleCut.consumeClick()) CutTool.get().toggle();
            while (keyPlace.consumeClick()) CutTool.get().releaseHeld();  // desktop fallback for left trigger
            while (keyChangeHand.consumeClick()) CutTool.get().changeHand();  // Quest A button
            while (keySettings.consumeClick()) Minecraft.getInstance().setScreen(new ViveInterfaceScreen());
            while (keyDebugDump.consumeClick()) DebugLog.dumpState();
            while (keyDebugToggle.consumeClick()) ViveConfig.toggleDebug();
            CutTool.get().tick();
        });

        DebugLog.log("INIT", "ready — N=cut mode, right trigger=cut, left hand grabs, "
                + "left/right trigger releases (M fallback), K=settings, J=debug dump, L=toggle logging");
    }
}
