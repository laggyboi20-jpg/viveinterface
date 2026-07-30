package com.laggy.viveinterface.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.laggy.viveinterface.debug.DebugLog;
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

    /** If your Vivecraft binds the grab/release triggers the other way, flip this. */
    public boolean swapTriggers = false;

    /**
     * ARGB backing drawn behind a placed piece and behind the cut screen's HUD image, so translucent
     * parts of the HUD read as a solid sheet instead of showing the world through them.
     * <b>Alpha 0 = no background</b> (fully see-through, the old behaviour).
     */
    public int backgroundColor = 0xE0101018;   // near-opaque dark slate

    /** Release a piece against a block and it lies flat on that face instead of sinking into it. */
    public boolean snapToBlocks = true;
    /** How far from a piece's centre a block face is still grabbed for snapping (metres). */
    public float snapRange = 0.35f;
    /** Gap kept between a piece and whatever it sits on — a block face, or your arm (metres). */
    public float surfaceClearance = 0.02f;

    // Placement geometry (metres).
    public float grabRadius = 0.07f;
    public float glueRadius = 0.18f;
    public float paperDistance = 0.45f;
    public float paperWidth = 0.55f;

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
                    INSTANCE.backgroundColor = in.backgroundColor;
                    INSTANCE.snapToBlocks = in.snapToBlocks;
                    if (in.snapRange > 0) INSTANCE.snapRange = in.snapRange;
                    if (in.surfaceClearance > 0) INSTANCE.surfaceClearance = in.surfaceClearance;
                    if (in.grabRadius > 0) INSTANCE.grabRadius = in.grabRadius;
                    if (in.glueRadius > 0) INSTANCE.glueRadius = in.glueRadius;
                    if (in.paperDistance > 0) INSTANCE.paperDistance = in.paperDistance;
                    if (in.paperWidth > 0) INSTANCE.paperWidth = in.paperWidth;
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
