package com.blokoyun.uxmfarmerautosmelt;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

final class ConfigurationMigration {
    private static final int CURRENT_VERSION = 4;
    private static final String DEFAULT_BUTTON_NAME = "&6ᴏᴛᴏ ᴇʀɪᴛᴍᴇ";
    private static final List<String> DEFAULT_BUTTON_LORE = List.of(
            "", "&7ᴜʀᴜɴ ᴍᴇɴᴜsᴜɴᴜ ᴀᴄᴍᴀᴋ ɪᴄɪɴ ᴛɪᴋʟᴀ", "", "&eᴇʀɪᴛᴍᴇ ᴍᴇɴᴜsᴜ ɪᴄɪɴ ᴛɪᴋʟᴀ");
    private static final String DEFAULT_PROGRESS_NAME = "&6ᴇʀɪᴛɪʟɪʏᴏʀ";

    private ConfigurationMigration() { }

    /** Apply once to the disk configuration, without JavaPlugin's resource defaults. */
    static boolean upgrade(FileConfiguration config) {
        int version = config.getInt("config-version", 0);
        if (version >= CURRENT_VERSION) return false;

        if (version < 3) {
            if (version < 1 && !config.contains("items.RAW_IRON")) {
                addPluginRecipe(config, "RAW_IRON", "IRON_INGOT");
            }
            migrateDefaultRecipe(config, "RAW_IRON", "IRON_INGOT", "give %player% iron_ingot %amount%");
            migrateDefaultRecipe(config, "IRON_ORE", "IRON_INGOT", "give %player% iron_ingot %amount%");
            migrateDefaultRecipe(config, "GOLD_ORE", "GOLD_INGOT", "give %player% gold_ingot %amount%");
            // Add only variants introduced in v3; later upgrades must not restore removed recipes.
            addIfAbsent(config, "DEEPSLATE_IRON_ORE", "IRON_INGOT");
            addIfAbsent(config, "RAW_GOLD", "GOLD_INGOT");
            addIfAbsent(config, "DEEPSLATE_GOLD_ORE", "GOLD_INGOT");
            addIfAbsent(config, "NETHER_GOLD_ORE", "GOLD_INGOT");
            addIfAbsent(config, "RAW_COPPER", "COPPER_INGOT");
            addIfAbsent(config, "COPPER_ORE", "COPPER_INGOT");
            addIfAbsent(config, "DEEPSLATE_COPPER_ORE", "COPPER_INGOT");
            migrateGuiText(config);
            if (!config.contains("automatic-smelting.enabled")) config.set("automatic-smelting.enabled", true);
            if (!config.contains("automatic-smelting.interval-ticks")) config.set("automatic-smelting.interval-ticks", 20);
        }
        if (!config.contains("automatic-smelting.permission")) {
            config.set("automatic-smelting.permission", RuleConfiguration.AUTO_PERMISSION);
        }
        if (!config.contains("instant-smelting.permission")) {
            config.set("instant-smelting.permission", RuleConfiguration.INSTANT_PERMISSION);
        }
        config.set("config-version", CURRENT_VERSION);
        return true;
    }

    private static void addIfAbsent(FileConfiguration config, String source, String output) {
        if (!config.contains("items." + source)) addPluginRecipe(config, source, output);
    }

    private static void addPluginRecipe(FileConfiguration config, String source, String output) {
        String path = "items." + source;
        config.set(path + ".mode", "plugin");
        config.set(path + ".output-material", output);
        config.set(path + ".min-amount", 1);
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

    private static void migrateGuiText(FileConfiguration config) {
        if (!config.contains("gui.title")) {
            config.set("gui.title", "<black><bold>Oto Eritme");
        }
        if (!config.contains("gui.filler-material")) {
            config.set("gui.filler-material", "GRAY_STAINED_GLASS_PANE");
        }
        String oldButton = config.getString("gui.button.name", "");
        if (DEFAULT_BUTTON_NAME.equals(oldButton)) {
            config.set("gui.button.name", "<gold>Oto Eritme");
        }
        List<String> lore = config.getStringList("gui.button.lore");
        if (DEFAULT_BUTTON_LORE.equals(lore)) {
            config.set("gui.button.lore", List.of("<gray>Eritme menüsünü açmak için tıkla"));
        }
        if (DEFAULT_PROGRESS_NAME.equals(config.getString("gui.progress.name", ""))) {
            config.set("gui.progress.name", "<gold>Eritiliyor");
        }
    }
}
