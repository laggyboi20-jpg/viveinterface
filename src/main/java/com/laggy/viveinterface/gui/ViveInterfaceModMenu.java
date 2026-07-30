package com.laggy.viveinterface.gui;

import com.laggy.viveinterface.config.ViveConfig;
import com.laggy.viveinterface.debug.DebugLog;
import com.laggy.viveinterface.panel.Panel;
import com.laggy.viveinterface.panel.PanelManager;
import com.laggy.viveinterface.panel.PanelStore;
import com.laggy.viveinterface.panel.Placement;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mod Menu → ViveInterface config screen, built with Cloth Config (matching the sibling
 * ViveMonkeCraft mod's settings style). This is where ALL of the mod's controls live — the only
 * in-game keybind left is <b>N</b> (toggle cut mode), because cut mode is fully modal in VR and you
 * can't open a 2D menu to get out of it.
 *
 * <p>Optional at runtime: if Mod Menu / Cloth Config aren't installed, the factory returns no screen
 * (the mod still runs, you just edit {@code config/viveinterface/settings.json} by hand). Each row
 * reads the live {@link ViveConfig} value and writes it back on Save; the whole thing persists via
 * {@link ViveConfig#save()} in the saving runnable.
 */
public class ViveInterfaceModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // No Cloth Config → no screen (never crash when the config button is clicked).
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")
                && !FabricLoader.getInstance().isModLoaded("cloth-config2")) {
            return parent -> null;
        }
        return this::build;
    }

    private Screen build(Screen parent) {
        ViveConfig c = ViveConfig.get();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("ViveInterface"));

        // Panels flagged for deletion on the Placed-pieces page; removed in the saving runnable.
        final List<Panel> toDelete = new ArrayList<>();

        builder.setSavingRunnable(() -> {
            DebugLog.ENABLED = c.debugLogging;        // apply the live logging flag
            if (!toDelete.isEmpty()) {
                for (Panel p : toDelete) PanelManager.remove(p);
                toDelete.clear();
            }
            ViveConfig.save();
            PanelStore.save();
        });

        ConfigEntryBuilder eb = builder.entryBuilder();

        // ================================ GENERAL ================================
        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));

        general.addEntry(eb.startTextDescription(Component.literal(
                "§7Press §fN§7 in-game to open the cut screen (Vivecraft shows it as the flat pointer "
                        + "panel). §fDrag a box§7 over the HUD with the pointer and press §fCut§7 to lift "
                        + "that region out as a floating panel. §fX / Done / Esc§7 closes it. Sizes and "
                        + "placements are tuned right here."))
                .build());

        general.addEntry(eb.startAlphaColorField(Component.literal("Piece background"), c.backgroundColor)
                .setDefaultValue(0xE0101018)
                .setTooltip(Component.literal("Colour drawn behind a cut piece and behind the cut screen, so "
                                + "translucent parts of the HUD don't show the world through them."),
                        Component.literal("§eSet the alpha to 0 for no background§7 — the piece then stays "
                                + "see-through like before."))
                .setSaveConsumer(v -> c.backgroundColor = v).build());

        general.addEntry(eb.startBooleanToggle(Component.literal("Swap cut / release triggers"), c.swapTriggers)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Flip this if your Vivecraft binds the release trigger to the "
                        + "other hand (used when carrying a placed piece)."))
                .setSaveConsumer(v -> c.swapTriggers = v).build());

        general.addEntry(eb.startBooleanToggle(Component.literal("Debug logging"), c.debugLogging)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Log cut / grab / release / snapshot / mask events to the game log "
                        + "under the 'ViveInterface' logger."))
                .setSaveConsumer(v -> c.debugLogging = v).build());

        // ============================ CUTTING GEOMETRY ============================
        ConfigCategory geo = builder.getOrCreateCategory(Component.literal("Cutting geometry"));

        geo.addEntry(floatField(eb, "Placed-piece distance (m)", c.paperDistance, 0.1f, 3.0f,
                "How far in front of you a freshly-cut piece is placed.",
                v -> c.paperDistance = v));
        geo.addEntry(floatField(eb, "Placed-piece base width (m)", c.paperWidth, 0.1f, 3.0f,
                "Base physical width a full-HUD cut would be; a smaller selection scales down from this. "
                        + "Height follows the selection's aspect ratio.",
                v -> c.paperWidth = v));
        geo.addEntry(eb.startBooleanToggle(Component.literal("Snap pieces to block surfaces"), c.snapToBlocks)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Release a piece against a wall or floor and it lies flat on "
                        + "that face instead of sinking into the block."))
                .setSaveConsumer(v -> c.snapToBlocks = v).build());
        geo.addEntry(floatField(eb, "Snap range (m)", c.snapRange, 0.05f, 1.5f,
                "How close a block face has to be for a released piece to snap onto it.",
                v -> c.snapRange = v));
        geo.addEntry(floatField(eb, "Surface gap (m)", c.surfaceClearance, 0.001f, 0.3f,
                "Gap kept between a piece and whatever it rests on — a block face, or your arm.",
                v -> c.surfaceClearance = v));
        geo.addEntry(floatField(eb, "Grab radius (m)", c.grabRadius, 0.01f, 0.5f,
                "Size of the off-hand grab sphere (used when repositioning a placed piece).",
                v -> c.grabRadius = v));
        geo.addEntry(floatField(eb, "Glue radius (m)", c.glueRadius, 0.01f, 1.0f,
                "How close to a hand/head you must release a piece for it to glue there.",
                v -> c.glueRadius = v));

        // ============================ TRANSFORM PAGES ============================
        // Where a piece sits once it's glued to a hand/head or carried (X/Y/Z + yaw/pitch/roll + scale).
        addPlacementCategory(builder, eb, "Hand panel default", c.handPanelPlace);
        addPlacementCategory(builder, eb, "Head panel default", c.headPanelPlace);
        addPlacementCategory(builder, eb, "Held piece default", c.heldPanelPlace);

        // ============================= PLACED PIECES =============================
        ConfigCategory pieces = builder.getOrCreateCategory(Component.literal("Placed pieces"));
        List<Panel> all = PanelManager.all();
        if (all.isEmpty()) {
            pieces.addEntry(eb.startTextDescription(Component.literal(
                    "§7No pieces placed yet. Cut some in VR (press §fN§7), then come back here to resize "
                            + "or delete them.")).build());
        } else {
            pieces.addEntry(eb.startTextDescription(Component.literal(
                    "§7One row per placed piece. Adjust its scale, or tick §fDelete§7 and press Save to remove it."))
                    .build());
            int i = 1;
            for (Panel p : new ArrayList<>(all)) {
                final Panel panel = p;
                String label = i + ". " + p.anchor;
                pieces.addEntry(floatField(eb, label + " — scale", p.scale, 0.05f, 5.0f,
                        "Size multiplier for this placed piece.",
                        v -> panel.scale = v));
                pieces.addEntry(eb.startBooleanToggle(Component.literal(label + " — delete"), false)
                        .setDefaultValue(false)
                        .setTooltip(Component.literal("Tick and press Save to remove this piece."))
                        .setSaveConsumer(v -> { if (v) toDelete.add(panel); }).build());
                i++;
            }
        }

        return builder.build();
    }

    /** Add a 7-row page (X/Y/Z metres + yaw/pitch/roll degrees + scale) that edits a {@link Placement} live. */
    private static void addPlacementCategory(ConfigBuilder builder, ConfigEntryBuilder eb,
                                             String name, Placement pl) {
        ConfigCategory cat = builder.getOrCreateCategory(Component.literal(name));
        cat.addEntry(floatField(eb, "Offset X (m)", pl.posX, -2f, 2f,
                "Left/right offset from the controller (in the rotated frame).", v -> pl.posX = v));
        cat.addEntry(floatField(eb, "Offset Y (m)", pl.posY, -2f, 2f,
                "Up/down offset from the controller.", v -> pl.posY = v));
        cat.addEntry(floatField(eb, "Offset Z (m)", pl.posZ, -2f, 2f,
                "Forward/back offset from the controller.", v -> pl.posZ = v));
        cat.addEntry(floatField(eb, "Yaw (°)", pl.yaw, -360f, 360f,
                "Rotation around the vertical axis.", v -> pl.yaw = v));
        cat.addEntry(floatField(eb, "Pitch (°)", pl.pitch, -360f, 360f,
                "Tilt up/down.", v -> pl.pitch = v));
        cat.addEntry(floatField(eb, "Roll (°)", pl.roll, -360f, 360f,
                "Bank left/right.", v -> pl.roll = v));
        cat.addEntry(floatField(eb, "Scale", pl.scale, 0.01f, 3f,
                "Size multiplier for this element.", v -> pl.scale = v));
    }

    /** A double field that reads/writes a {@code float} config value (Cloth only has double/int fields). */
    private interface FloatSetter { void set(float v); }

    private static me.shedaniel.clothconfig2.api.AbstractConfigListEntry<?> floatField(
            ConfigEntryBuilder eb, String label, float value, float min, float max,
            String tooltip, FloatSetter setter) {
        return eb.startDoubleField(Component.literal(label), value)
                .setDefaultValue((double) value)
                .setMin(min).setMax(max)
                .setTooltip(Component.literal(tooltip))
                .setSaveConsumer(v -> setter.set(v.floatValue()))
                .build();
    }
}
