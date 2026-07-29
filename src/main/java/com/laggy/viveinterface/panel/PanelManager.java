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

    public static void remove(Panel p) {
        PANELS.remove(p);
    }

    public static void clear() {
        PANELS.clear();
    }
}
