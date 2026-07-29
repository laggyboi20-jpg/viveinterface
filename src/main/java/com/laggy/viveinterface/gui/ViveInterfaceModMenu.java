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
                "§7Press §fN§7 in-game to toggle cut mode (menu + sword). In cut mode, use the "
                        + "§fright trigger§7 to cut and reach your §foff hand§7 into a piece to grab it; "
                        + "§feither trigger§7 releases. Everything else is tuned right here."))
                .build());

        general.addEntry(eb.startBooleanToggle(Component.literal("Real item models (sword / stick)"), c.realModels)
                .setDefaultValue(true)
                .setTooltip(Component.literal("ON = draw the wooden sword & selection stick as real item models "
                        + "(tune their position on the transform pages). OFF = simple coloured quads."))
                .setSaveConsumer(v -> c.realModels = v).build());

        general.addEntry(eb.startBooleanToggle(Component.literal("Swap cut / release triggers"), c.swapTriggers)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Flip this if your Vivecraft binds the cut trigger to the off hand. "
                        + "Default: dominant trigger = ATTACK = cut, off trigger = USE = release."))
                .setSaveConsumer(v -> c.swapTriggers = v).build());

        general.addEntry(eb.startBooleanToggle(Component.literal("Debug logging"), c.debugLogging)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Log cut / grab / release / snapshot / mask events to the game log "
                        + "under the 'ViveInterface' logger."))
                .setSaveConsumer(v -> c.debugLogging = v).build());

        // ============================ CUTTING GEOMETRY ============================
        ConfigCategory geo = builder.getOrCreateCategory(Component.literal("Cutting geometry"));

        geo.addEntry(floatField(eb, "Grab radius (m)", c.grabRadius, 0.01f, 0.5f,
                "Size of the off-hand grab sphere. Bigger = easier to grab a floating piece.",
                v -> c.grabRadius = v));
        geo.addEntry(floatField(eb, "Glue radius (m)", c.glueRadius, 0.01f, 1.0f,
                "How close to a hand/head you must release a piece for it to glue there (else it drops in the world).",
                v -> c.glueRadius = v));
        geo.addEntry(floatField(eb, "Blade length (m)", c.bladeLength, 0.05f, 1.5f,
                "Length of the cutting sword blade.",
                v -> c.bladeLength = v));
        geo.addEntry(floatField(eb, "Selection stick length (m)", c.selectStickLength, 0.02f, 1.0f,
                "Length of the off-hand pointer stick used to grab pieces.",
                v -> c.selectStickLength = v));
        geo.addEntry(floatField(eb, "Menu distance (m)", c.paperDistance, 0.1f, 2.0f,
                "How far in front of you the cut menu (the HUD on a dark backing) spawns.",
                v -> c.paperDistance = v));
        geo.addEntry(floatField(eb, "Menu width (m)", c.paperWidth, 0.1f, 2.0f,
                "Physical width of the cut menu; height follows the HUD aspect ratio.",
                v -> c.paperWidth = v));

        // ============================ TRANSFORM PAGES ============================
        // The "sword spawns at my body not my hand" fix lives here: nudge X/Y/Z + yaw/pitch/roll
        // until the model sits right on the controller.
        addPlacementCategory(builder, eb, "Sword transform", c.swordPlace);
        addPlacementCategory(builder, eb, "Stick transform", c.stickPlace);
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
