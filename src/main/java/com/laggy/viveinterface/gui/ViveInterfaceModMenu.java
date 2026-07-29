package com.laggy.viveinterface.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu entry point → opens the global settings screen (which links to the per-piece editor).
 * Mod Menu is optional at runtime; this class is only loaded when Mod Menu is present.
 */
public class ViveInterfaceModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return GlobalSettingsScreen::new;   // parent -> new GlobalSettingsScreen(parent)
    }
}
