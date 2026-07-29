package com.laggy.viveinterface.panel;

/** Where a placed panel lives. */
public enum PanelAnchor {
    /** Follows the dominant (sword) hand. */
    MAIN_HAND,
    /** Follows the off hand — used while a freshly-cut piece is being carried. */
    OFF_HAND,
    /** Follows the head/HMD. */
    HEAD,
    /** Fixed in the world at the transform captured when it was dropped. */
    WORLD
}
