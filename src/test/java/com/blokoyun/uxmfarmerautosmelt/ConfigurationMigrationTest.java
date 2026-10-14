package com.blokoyun.uxmfarmerautosmelt;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationMigrationTest {
    @Test
    void legacyGiveRecipesBecomeStorageRecipesWithoutChangingLimits() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                config-version: 1
                gui:
                  duration-seconds: 20
                items:
                  RAW_IRON:
                    mode: commands
                    consume: true
                    min-amount: 3
                    batch-size: 16
                    commands: ['give %player% iron_ingot %amount%']
                  IRON_ORE:
                    mode: commands
                    consume: true
                    min-amount: 12
                    commands: ['give %player% iron_ingot %amount%']
                  GOLD_ORE:
                    mode: commands
                    consume: true
                    permission: smelt.gold
                    commands: ['give %player% gold_ingot %amount%']
                """);
        assertTrue(ConfigurationMigration.upgrade(config));
        for (String material : List.of("RAW_IRON", "IRON_ORE", "GOLD_ORE")) {
            assertEquals("plugin", config.getString("items." + material + ".mode"));
            assertFalse(config.contains("items." + material + ".commands"));
            assertFalse(config.contains("items." + material + ".consume"));
        }
        assertEquals("IRON_INGOT", config.getString("items.RAW_IRON.output-material"));
        assertEquals("IRON_INGOT", config.getString("items.IRON_ORE.output-material"));
        assertEquals("GOLD_INGOT", config.getString("items.GOLD_ORE.output-material"));
        assertEquals(3, config.getInt("items.RAW_IRON.min-amount"));
        assertEquals(16, config.getInt("items.RAW_IRON.batch-size"));
        assertEquals(12, config.getInt("items.IRON_ORE.min-amount"));
        assertEquals("smelt.gold", config.getString("items.GOLD_ORE.permission"));
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
        assertTrue(ConfigurationMigration.upgrade(config));
        assertFalse(ConfigurationMigration.upgrade(config));
        assertFalse(config.contains("items.RAW_IRON"));
    }

    @Test
    void customizedCommandRecipeIsNotReplaced() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("config-version", 1);
        config.set("items.RAW_IRON.mode", "commands");
        config.set("items.RAW_IRON.consume", true);
        config.set("items.RAW_IRON.commands", List.of("custom-reward %player% %amount%"));
        Map<String, Object> recipe = Map.copyOf(config.getConfigurationSection("items.RAW_IRON").getValues(true));
        assertTrue(ConfigurationMigration.upgrade(config));
        assertEquals(recipe, config.getConfigurationSection("items.RAW_IRON").getValues(true));
    }

    @Test
    void unmigratedConfigGetsStorageRecipeWithoutChangingOtherSettings() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("gui.duration-seconds", 20);
        config.set("items.IRON_ORE.min-amount", 12);
        assertTrue(ConfigurationMigration.upgrade(config));
        assertEquals("plugin", config.getString("items.RAW_IRON.mode"));
        assertEquals("IRON_INGOT", config.getString("items.RAW_IRON.output-material"));
        assertEquals(20, config.getInt("gui.duration-seconds"));
        assertEquals(12, config.getInt("items.IRON_ORE.min-amount"));
    }
}
