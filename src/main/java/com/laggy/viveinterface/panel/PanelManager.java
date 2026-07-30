package com.laggy.viveinterface.panel;

import java.util.ArrayList;
import java.util.List;

/** Holds all placed panels for the session. (Persistence to disk is a later step.) */
public final class PanelManager {

    private static final List<Panel> PANELS = new ArrayList<>();

    private PanelManager() {}

    public static List<Panel> all() {
        return PANELS;
    }

    public static void add(Panel p) {
        PANELS.add(p);
    }

    /** The panel with this id, or null. */
    public static Panel byId(java.util.UUID id) {
        if (id == null) return null;
        for (Panel p : PANELS) if (id.equals(p.id)) return p;
        return null;
    }

    /**
     * Remove a panel. Anything stuck to it is first baked to a static world transform, so deleting a
     * piece never leaves its children floating unresolvable.
     */
    public static void remove(Panel p) {
        if (p == null) return;
        for (Panel child : new ArrayList<>(PANELS)) {
            if (child != p && child.anchor == PanelAnchor.PANEL && p.id.equals(child.parentId)) {
                child.dropToWorld();
            }
        }
        PANELS.remove(p);
    }

    public static void clear() {
        PANELS.clear();
    }
}
