package com.blokoyun.hooks.farmersmelt.config;

import com.blokoyun.hooks.farmersmelt.smelt.ItemRule;
import com.blokoyun.hooks.farmersmelt.smelt.SmeltRecipe;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public final class RuleConfiguration {
    public static final String AUTO_PERMISSION = "uxmfarmerotosmelter.auto";
    public static final String INSTANT_PERMISSION = "uxmfarmerotosmelter.instant";
    private static final int CURRENT_SCHEMA = 4;
    private final boolean enabled;
    private final boolean automatic;
    private final boolean autoSmeltEnabled;
    private final long autoSmeltIntervalTicks;
    private final String autoSmeltPermission;
    private final String instantSmeltPermission;
    private final String pluginName;
    private final String eventClassName;
    private final String priorityName;
    private final ItemRule defaults;
    private final Map<Material, ItemRule> itemRules;
    private final List<SmeltRecipe> recipes;
    private final GuiSettings guiSettings;

    private RuleConfiguration(FileConfiguration config, ItemRule defaults, Map<Material, ItemRule> rules,
                             List<SmeltRecipe> recipes, GuiSettings gui) {
        validateSchema(config);
        enabled = readBoolean(config, "enabled", true);
        String trigger = required(readString(config, "trigger", "click", "trigger"), "trigger")
                .toLowerCase(Locale.ROOT);
        if (!trigger.equals("click") && !trigger.equals("automatic")) {
            throw new IllegalArgumentException("trigger yalnızca click veya automatic olabilir");
        }
        automatic = trigger.equals("automatic");
        ConfigurationSection automaticSection = section(config, "automatic-smelting");
        autoSmeltEnabled = readBoolean(automaticSection, "enabled", true, "automatic-smelting.enabled");
        autoSmeltIntervalTicks = positiveTicks(readLong(automaticSection, "interval-ticks", 20L,
                "automatic-smelting.interval-ticks"));
        autoSmeltPermission = readString(automaticSection, "permission", AUTO_PERMISSION,
                "automatic-smelting.permission").trim();
        ConfigurationSection instantSection = section(config, "instant-smelting");
        instantSmeltPermission = readString(instantSection, "permission", INSTANT_PERMISSION,
                "instant-smelting.permission").trim();
        ConfigurationSection hook = section(config, "hook");
        pluginName = required(readString(hook, "plugin-name", "uxmFarmer", "hook.plugin-name"), "hook.plugin-name");
        eventClassName = required(readString(hook, "event-class",
                "xyz.geik.farmer.api.handlers.FarmerItemCollectEvent", "hook.event-class"), "hook.event-class");
        priorityName = required(readString(hook, "priority", "NORMAL", "hook.priority"), "hook.priority");
        try {
            org.bukkit.event.EventPriority.valueOf(priorityName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("hook.priority geçersiz: " + priorityName, ex);
        }
        this.defaults = defaults;
        itemRules = Map.copyOf(rules);
        this.recipes = List.copyOf(recipes);
        guiSettings = gui;
    }


    public static RuleConfiguration load(FileConfiguration config, Logger logger) {
        validateSchema(config);
        GuiSettings gui = GuiSettings.load(config);
        ConfigurationSection defaultsSection = section(config, "defaults");
        ItemRule fallback = new ItemRule(null, ItemRule.RuleMode.PLUGIN, 1, 0, true, "", List.of());
        ItemRule defaults = parseRule(defaultsSection, null, fallback, "defaults");
        Map<Material, ItemRule> rules = new EnumMap<>(Material.class);
        List<SmeltRecipe> recipes = new ArrayList<>();
        ConfigurationSection items = section(config, "items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                String path = "items." + key;
                Material material = Material.matchMaterial(key);
                if (material == null || !material.isItem()) {
                    throw new IllegalArgumentException(path + " geçersiz material");
                }
                ConfigurationSection itemSection = section(items, key, path);
                ItemRule rule = parseRule(itemSection, material, defaults, path);
                Material output = SmeltRecipe.defaultOutput(material);
                if (itemSection.contains("output-material")) {
                    output = Material.matchMaterial(readString(itemSection, "output-material", null,
                            path + ".output-material"));
                }
                if (rule.mode() == ItemRule.RuleMode.PLUGIN
                        && (output == null || !output.isItem() || output == material)) {
                    throw new IllegalArgumentException(path
                            + " plugin modu için geçerli, farklı output-material gerekli");
                }
                if (rule.mode() == ItemRule.RuleMode.COMMANDS && rule.commands().isEmpty()) {
                    throw new IllegalArgumentException(path + " commands modu için boş olmayan komut listesi gerekli");
                }
                int seconds = readInt(itemSection, "duration-seconds", gui.durationSeconds(), path + ".duration-seconds");
                int batch = readInt(itemSection, "batch-size", gui.batchSize(), path + ".batch-size");
                rules.put(material, rule);
                recipes.add(new SmeltRecipe(rule, output, seconds, batch));
            }
        }
        return new RuleConfiguration(config, defaults, rules, recipes, gui);
    }

    private static ItemRule parseRule(ConfigurationSection section, Material material, ItemRule fallback, String path) {
        if (section == null) return fallback.withMaterial(material);
        String mode = readString(section, "mode", fallback.mode().name(), path + ".mode");
        ItemRule.RuleMode parsed;
        try {
            parsed = ItemRule.RuleMode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(path + ".mode geçersiz: " + mode, ex);
        }
        int min = readInt(section, "min-amount", fallback.minAmount(), path + ".min-amount");
        int max = readInt(section, "max-amount", fallback.maxAmount(), path + ".max-amount");
        boolean consume = readBoolean(section, "consume", fallback.consume(), path + ".consume");
        String permission = readString(section, "permission", fallback.permission(), path + ".permission");
        List<String> commands = section.contains("commands")
                ? readStringList(section, "commands", path + ".commands") : fallback.commands();
        return new ItemRule(material, parsed, min, max, consume, permission, commands);
    }

    private static void validateSchema(ConfigurationSection config) {
        if (!config.contains("config-version")) return;
        int version = readInt(config, "config-version", -1, "config-version");
        if (version < 0 || version > CURRENT_SCHEMA) {
            throw new IllegalArgumentException("config-version desteklenmiyor: " + version);
        }
    }

    private static ConfigurationSection section(ConfigurationSection parent, String key) {
        if (parent == null || !parent.contains(key)) return null;
        return section(parent, key, key);
    }

    private static ConfigurationSection section(ConfigurationSection parent, String key, String path) {
        Object value = parent.get(key);
        if (value instanceof ConfigurationSection section) return section;
        throw new IllegalArgumentException(path + " bir bölüm olmalı");
    }

    private static String required(String value, String path) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(path + " boş olamaz");
        return value.trim();
    }

    private static boolean readBoolean(ConfigurationSection section, String key, boolean fallback, String path) {
        if (section == null || !section.contains(key)) return fallback;
        Object value = section.get(key);
        if (!(value instanceof Boolean)) throw new IllegalArgumentException(path + " boolean olmalı");
        return (Boolean) value;
    }

    private static boolean readBoolean(ConfigurationSection section, String key, boolean fallback) {
        return readBoolean(section, key, fallback, key);
    }

    private static int readInt(ConfigurationSection section, String key, int fallback, String path) {
        if (section == null || !section.contains(key)) return fallback;
        long value = integral(section.get(key), path);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(path + " integer aralığı dışında");
        }
        return (int) value;
    }

    private static long readLong(ConfigurationSection section, String key, long fallback, String path) {
        if (section == null || !section.contains(key)) return fallback;
        return integral(section.get(key), path);
    }

    private static long integral(Object value, String path) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) {
            throw new IllegalArgumentException(path + " integer olmalı");
        }
        return ((Number) value).longValue();
    }

    private static String readString(ConfigurationSection section, String key, String fallback, String path) {
        if (section == null || !section.contains(key)) return fallback;
        Object value = section.get(key);
        if (!(value instanceof String)) throw new IllegalArgumentException(path + " metin olmalı");
        return (String) value;
    }

    private static List<String> readStringList(ConfigurationSection section, String key, String path) {
        Object value = section.get(key);
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(path + " metin listesi olmalı");
        List<String> result = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (!(entry instanceof String)) throw new IllegalArgumentException(path + " yalnızca metin içermeli");
            result.add((String) entry);
        }
        return List.copyOf(result);
    }

    private static long positiveTicks(long value) {
        if (value <= 0) throw new IllegalArgumentException("automatic-smelting.interval-ticks pozitif olmalı");
        return value;
    }

    public boolean enabled() { return enabled; }
    public boolean automatic() { return automatic; }
    public boolean autoSmeltEnabled() { return autoSmeltEnabled; }
    public long autoSmeltIntervalTicks() { return autoSmeltIntervalTicks; }
    public String autoSmeltPermission() { return autoSmeltPermission; }
    public String instantSmeltPermission() { return instantSmeltPermission; }
    public String pluginName() { return pluginName; }
    public String eventClassName() { return eventClassName; }
    public String priorityName() { return priorityName; }
    public int itemCount() { return itemRules.size(); }
    public ItemRule ruleFor(Material material) { return itemRules.getOrDefault(material, defaults.withMaterial(material)); }
    public List<SmeltRecipe> recipes() { return recipes; }
    public GuiSettings guiSettings() { return guiSettings; }

    public record GuiSettings(boolean enabled, int slot, int rows, int durationSeconds, int batchSize,
                              String permission, Material buttonMaterial, String buttonName, List<String> buttonLore,
                              String progressName, int barLength, String title, Material fillerMaterial) {
        private static final String DEFAULT_TITLE = "<black><bold>Oto Eritme";
        private static final Material DEFAULT_FILLER = Material.GRAY_STAINED_GLASS_PANE;


        static GuiSettings load(FileConfiguration config) {
            section(config, "gui");
            ConfigurationSection button = section(config, "gui.button");
            ConfigurationSection progress = section(config, "gui.progress");
            int rows = readInt(config, "gui.rows", 6, "gui.rows");
            if (rows < 3 || rows > 6) throw new IllegalArgumentException("gui.rows 3..6 arasında olmalı");
            int slot = readInt(config, "gui.slot", 7, "gui.slot");
            if (slot < 0 || slot >= rows * 9) throw new IllegalArgumentException("gui.slot menü boyutunun dışında");
            Material icon = Material.matchMaterial(readString(button, "material", "BLAST_FURNACE", "gui.button.material"));
            if (icon == null || !icon.isItem() || icon.isAir()) throw new IllegalArgumentException("gui.button.material geçersiz");
            Material filler = Material.matchMaterial(readString(config, "gui.filler-material",
                    DEFAULT_FILLER.name(), "gui.filler-material"));
            if (filler == null || !filler.isItem() || filler.isAir()) throw new IllegalArgumentException("gui.filler-material geçersiz");
            int duration = readInt(config, "gui.duration-seconds", 10, "gui.duration-seconds");
            if (duration < 1 || duration > 86400) throw new IllegalArgumentException("gui.duration-seconds 1..86400 arasında olmalı");
            int batch = readInt(config, "gui.batch-size", 64, "gui.batch-size");
            if (batch < 0) throw new IllegalArgumentException("gui.batch-size negatif olamaz");
            int barLength = readInt(progress, "bar-length", 20, "gui.progress.bar-length");
            if (barLength < 1 || barLength > 50) throw new IllegalArgumentException("gui.progress.bar-length 1..50 arasında olmalı");
            String buttonName = readString(button, "name", "<gold>Oto Eritme", "gui.button.name");
            List<String> buttonLore = button != null && button.contains("lore")
                    ? readStringList(button, "lore", "gui.button.lore")
                    : List.of("<gray>Eritme menüsünü açmak için tıkla");
            String progressName = readString(progress, "name", "<gold>Eritiliyor", "gui.progress.name");
            return new GuiSettings(readBoolean(config, "gui.enabled", true, "gui.enabled"), slot, rows, duration, batch,
                    readString(config, "gui.permission", "uxmfarmerotosmelter.use", "gui.permission"), icon,
                    buttonName, buttonLore, progressName, barLength,
                    readString(config, "gui.title", DEFAULT_TITLE, "gui.title"), filler);
        }
    }
}
