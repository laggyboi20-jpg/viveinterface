package com.laggy.viveinterface.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.laggy.viveinterface.debug.DebugLog;
import com.laggy.viveinterface.panel.Placement;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Global (non-per-piece) settings, saved to {@code config/viveinterface/settings.json}. Currently just
 * the debug-logging toggle; a home for future global options. Applying a value also updates the live
 * {@link DebugLog} flag.
 */
public final class ViveConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ViveConfig INSTANCE = new ViveConfig();

    /** Mirrors {@link DebugLog#ENABLED}; persisted so it survives relogging. */
    public boolean debugLogging = true;

    /** If your Vivecraft binds the cut/release triggers the other way, flip this. */
    public boolean swapTriggers = false;
    /** Render the sword/stick as real item models (else simple coloured quads). */
    public boolean realModels = true;

    /**
     * ARGB backing drawn behind a placed piece and behind the cut screen's HUD image, so translucent
     * parts of the HUD read as a solid sheet instead of showing the world through them.
     * <b>Alpha 0 = no background</b> (fully see-through, the old behaviour).
     */
    public int backgroundColor = 0xE0101018;   // near-opaque dark slate

    // Cutting geometry (metres).
    public float bladeLength = 0.42f;
    public float selectStickLength = 0.16f;
    public float grabRadius = 0.07f;
    public float glueRadius = 0.18f;
    public float paperDistance = 0.45f;
    public float paperWidth = 0.55f;

    // Per-element transforms (XYZ offset + yaw/pitch/roll + scale), editable in the settings screen.
    public Placement swordPlace = new Placement(0f, 0f, 0f, 0f, -90f, 0f, 0.67f);   // held sword model
    public Placement stickPlace = new Placement(0f, 0f, 0f, 0f, -90f, 0f, 0.26f);   // selection stick model
    public Placement handPanelPlace = Placement.onHand();   // default for a panel glued to the main hand
    public Placement heldPanelPlace = Placement.held();     // default for a piece carried on the off hand
    public Placement headPanelPlace = Placement.onHead();   // default for a panel worn on the head

    private ViveConfig() {}

    public static ViveConfig get() { return INSTANCE; }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("viveinterface").resolve("settings.json");
    }

    public static void load() {
        Path f = file();
        if (Files.exists(f)) {
            try {
                ViveConfig in = GSON.fromJson(Files.readString(f), ViveConfig.class);
                if (in != null) {
                    INSTANCE.debugLogging = in.debugLogging;
                    INSTANCE.swapTriggers = in.swapTriggers;
                    INSTANCE.realModels = in.realModels;
                    INSTANCE.backgroundColor = in.backgroundColor;
                    if (in.bladeLength > 0) INSTANCE.bladeLength = in.bladeLength;
                    if (in.selectStickLength > 0) INSTANCE.selectStickLength = in.selectStickLength;
                    if (in.grabRadius > 0) INSTANCE.grabRadius = in.grabRadius;
                    if (in.glueRadius > 0) INSTANCE.glueRadius = in.glueRadius;
                    if (in.paperDistance > 0) INSTANCE.paperDistance = in.paperDistance;
                    if (in.paperWidth > 0) INSTANCE.paperWidth = in.paperWidth;
                    if (in.swordPlace != null) INSTANCE.swordPlace = in.swordPlace;
                    if (in.stickPlace != null) INSTANCE.stickPlace = in.stickPlace;
                    if (in.handPanelPlace != null) INSTANCE.handPanelPlace = in.handPanelPlace;
                    if (in.heldPanelPlace != null) INSTANCE.heldPanelPlace = in.heldPanelPlace;
                    if (in.headPanelPlace != null) INSTANCE.headPanelPlace = in.headPanelPlace;
                }
            } catch (Exception e) {
                DebugLog.error("CONFIG", "failed to load settings", e);
            }
        }
        DebugLog.ENABLED = INSTANCE.debugLogging;
    }

    public static void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            DebugLog.error("CONFIG", "failed to save settings", e);
        }
    }

    /** Flip debug logging, apply it live, and persist. */
    public static void toggleDebug() {
        INSTANCE.debugLogging = !INSTANCE.debugLogging;
        DebugLog.ENABLED = INSTANCE.debugLogging;
        save();
        DebugLog.announceState();
    }
}
