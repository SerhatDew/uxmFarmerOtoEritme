package com.blokoyun.uxmfarmerautosmelt;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

final class ConfigurationMigration {
    private ConfigurationMigration() { }

    /** Apply once to the disk configuration, without JavaPlugin's resource defaults. */
    static boolean upgrade(FileConfiguration config) {
        if (config.getInt("config-version", 0) >= 1) return false;
        if (!config.contains("items.RAW_IRON")) {
            config.set("items.RAW_IRON.mode", "commands");
            config.set("items.RAW_IRON.min-amount", 1);
            config.set("items.RAW_IRON.consume", true);
            config.set("items.RAW_IRON.commands", List.of("give %player% iron_ingot %amount%"));
        }
        config.set("config-version", 1);
        return true;
    }
}
