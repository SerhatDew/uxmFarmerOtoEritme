package com.blokoyun.uxmfarmerautosmelt;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FarmerDependencyTest {
    @Test
    void oldConfigFindsModernFarmerPlugin() {
        PluginManager manager = mock(PluginManager.class);
        Plugin modern = mock(Plugin.class);
        when(manager.getPlugin("uxmFarmer")).thenReturn(modern);

        assertSame(modern, FarmerDependency.find(manager, "Farmer"));
    }

    @Test
    void modernConfigFindsOlderFarmerPlugin() {
        PluginManager manager = mock(PluginManager.class);
        Plugin older = mock(Plugin.class);
        when(manager.getPlugin("Farmer")).thenReturn(older);

        assertSame(older, FarmerDependency.find(manager, "uxmFarmer"));
    }

    @Test
    void configuredPluginTakesPrecedence() {
        PluginManager manager = mock(PluginManager.class);
        Plugin custom = mock(Plugin.class);
        when(manager.getPlugin("CustomFarmer")).thenReturn(custom);

        assertSame(custom, FarmerDependency.find(manager, "CustomFarmer"));
    }
}
