package com.blokoyun.uxmfarmerautosmelt;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

final class ConfigurationMigration {
    private static final int CURRENT_VERSION = 2;

    private ConfigurationMigration() { }

    /** Apply once to the disk configuration, without JavaPlugin's resource defaults. */
    static boolean upgrade(FileConfiguration config) {
        int version = config.getInt("config-version", 0);
        if (version >= CURRENT_VERSION) return false;
        if (version < 1 && !config.contains("items.RAW_IRON")) {
            config.set("items.RAW_IRON.mode", "plugin");
            config.set("items.RAW_IRON.output-material", "IRON_INGOT");
            config.set("items.RAW_IRON.min-amount", 1);
        }
        migrateDefaultRecipe(config, "RAW_IRON", "IRON_INGOT", "give %player% iron_ingot %amount%");
        migrateDefaultRecipe(config, "IRON_ORE", "IRON_INGOT", "give %player% iron_ingot %amount%");
        migrateDefaultRecipe(config, "GOLD_ORE", "GOLD_INGOT", "give %player% gold_ingot %amount%");
        config.set("config-version", CURRENT_VERSION);
        return true;
    }

    private static void migrateDefaultRecipe(FileConfiguration config, String source, String output,
                                             String oldCommand) {
        String path = "items." + source;
        List<String> commands = config.getStringList(path + ".commands");
        if (!"commands".equalsIgnoreCase(config.getString(path + ".mode", ""))
                || !config.getBoolean(path + ".consume", false)
                || commands.size() != 1 || !oldCommand.equals(commands.getFirst())) {
            return;
        }
        config.set(path + ".mode", "plugin");
        config.set(path + ".output-material", output);
        config.set(path + ".consume", null);
        config.set(path + ".commands", null);
    }
}
