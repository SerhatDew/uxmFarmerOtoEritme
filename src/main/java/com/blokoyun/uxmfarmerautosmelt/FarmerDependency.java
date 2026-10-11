package com.blokoyun.uxmfarmerautosmelt;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

final class FarmerDependency {
    private FarmerDependency() { }

    static Plugin find(PluginManager manager, String configuredName) {
        Plugin configured = manager.getPlugin(configuredName);
        if (configured != null) return configured;
        // Existing installations may still name the older Farmer plugin.
        Plugin modern = manager.getPlugin("uxmFarmer");
        return modern != null ? modern : manager.getPlugin("Farmer");
    }
}
