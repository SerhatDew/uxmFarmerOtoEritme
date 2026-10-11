package com.blokoyun.uxmfarmerautosmelt;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationMigrationTest {
    @Test
    void existingConfigGetsRawIronRecipeWithoutChangingOtherRules() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set("items.IRON_ORE.min-amount", 12);
        config.set("gui.duration-seconds", 20);
        assertTrue(ConfigurationMigration.upgrade(config));
        assertEquals("commands", config.getString("items.RAW_IRON.mode"));
        assertEquals(List.of("give %player% iron_ingot %amount%"), config.getStringList("items.RAW_IRON.commands"));
        assertEquals(1, config.getInt("items.RAW_IRON.min-amount"));
        assertEquals(12, config.getInt("items.IRON_ORE.min-amount"));
        assertEquals(20, config.getInt("gui.duration-seconds"));
        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(config.saveToString());
        assertFalse(ConfigurationMigration.upgrade(reloaded));
    }

    @Test
    void customRawIronRecipeIsPreserved() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("items.RAW_IRON.mode", "plugin");
        config.set("items.RAW_IRON.min-amount", 5);
        config.set("items.RAW_IRON.output-material", "IRON_INGOT");
        assertTrue(ConfigurationMigration.upgrade(config));
        assertEquals("plugin", config.getString("items.RAW_IRON.mode"));
        assertEquals(5, config.getInt("items.RAW_IRON.min-amount"));
        assertFalse(config.contains("items.RAW_IRON.commands"));
    }

    @Test
    void intentionallyRemovedRecipeIsNotReaddedAfterMigration() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("config-version", 1);
        assertFalse(ConfigurationMigration.upgrade(config));
        assertFalse(config.contains("items.RAW_IRON"));
    }
}
