package com.laggy.viveinterface.panel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.laggy.viveinterface.debug.DebugLog;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves/loads placed panels to {@code config/viveinterface/panels.json} so cut pieces survive
 * relogging — the UV rectangles are re-sampled from the live HUD each session, so a minimap glued to
 * your arm comes back live without re-cutting.
 */
public final class PanelStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PanelStore() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("viveinterface").resolve("panels.json");
    }

    /** Serializable form of a Panel. */
    private static final class Dto {
        float u0, v0, u1, v1;
        String anchor;
        float[] worldPos, worldRot, userOffset;
        float[] place;   // posX, posY, posZ, yaw, pitch, roll
        float[] relPos;  // body-relative offset (non-WORLD anchors)
        float[] relRot;  // body-relative rotation quaternion x,y,z,w
        float widthMeters, scale;
        String id, parentId;
    }

    public static void save() {
        List<Dto> out = new ArrayList<>();
        for (Panel p : PanelManager.all()) out.add(toDto(p));
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(out));
            DebugLog.logf("STORE", "saved %d panel(s)", out.size());
        } catch (IOException e) {
            DebugLog.error("STORE", "failed to save panels", e);
        }
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            Dto[] in = GSON.fromJson(Files.readString(f), Dto[].class);
            if (in == null) return;
            PanelManager.clear();
            for (Dto d : in) PanelManager.add(fromDto(d));
            // Body anchors are kept as-is: a piece you stuck to a hand or your head has to come back
            // stuck there after a restart. (An earlier build pinned them all to the world on load —
            // that was a workaround for stale data from before grabbing existed, and it silently threw
            // away real placements.)
            DebugLog.logf("STORE", "loaded %d saved panel(s)", in.length);
        } catch (Exception e) {
            DebugLog.error("STORE", "failed to load panels", e);
        }
    }

    private static Dto toDto(Panel p) {
        Dto d = new Dto();
        d.u0 = p.u0; d.v0 = p.v0; d.u1 = p.u1; d.v1 = p.v1;
        d.anchor = p.anchor.name();
        d.widthMeters = p.widthMeters;
        d.scale = p.scale;
        d.worldPos = new float[]{p.worldPos.x, p.worldPos.y, p.worldPos.z};
        d.worldRot = new float[]{p.worldRot.x, p.worldRot.y, p.worldRot.z, p.worldRot.w};
        d.userOffset = new float[]{p.userOffset.x, p.userOffset.y, p.userOffset.z};
        d.place = new float[]{p.place.posX, p.place.posY, p.place.posZ, p.place.yaw, p.place.pitch, p.place.roll};
        d.relPos = new float[]{p.relPos.x, p.relPos.y, p.relPos.z};
        d.relRot = new float[]{p.relRot.x, p.relRot.y, p.relRot.z, p.relRot.w};
        d.id = p.id.toString();
        d.parentId = (p.parentId == null) ? null : p.parentId.toString();
        return d;
    }

    private static Panel fromDto(Dto d) {
        Panel p = new Panel(d.u0, d.v0, d.u1, d.v1, PanelAnchor.valueOf(d.anchor));
        p.widthMeters = d.widthMeters;
        if (d.scale > 0) p.scale = d.scale;
        if (d.worldPos != null) p.worldPos.set(d.worldPos[0], d.worldPos[1], d.worldPos[2]);
        if (d.worldRot != null) p.worldRot.set(d.worldRot[0], d.worldRot[1], d.worldRot[2], d.worldRot[3]);
        if (d.userOffset != null) p.userOffset.set(d.userOffset[0], d.userOffset[1], d.userOffset[2]);
        if (d.place != null && d.place.length >= 6) {
            p.place = new Placement(d.place[0], d.place[1], d.place[2], d.place[3], d.place[4], d.place[5]);
        }
        if (d.relPos != null && d.relPos.length >= 3 && d.relRot != null && d.relRot.length >= 4) {
            p.relPos.set(d.relPos[0], d.relPos[1], d.relPos[2]);
            p.relRot.set(d.relRot[0], d.relRot[1], d.relRot[2], d.relRot[3]);
        } else {
            p.applyPlacement();   // older file: derive the body-relative transform from `place`
        }
        // Older files have no ids; the fresh random one from the constructor is fine there.
        try {
            if (d.id != null) p.id = java.util.UUID.fromString(d.id);
            if (d.parentId != null) p.parentId = java.util.UUID.fromString(d.parentId);
        } catch (IllegalArgumentException ignored) { }
        if (p.anchor == PanelAnchor.PANEL && p.parentId == null) p.anchor = PanelAnchor.WORLD;
        return p;
    }
}
