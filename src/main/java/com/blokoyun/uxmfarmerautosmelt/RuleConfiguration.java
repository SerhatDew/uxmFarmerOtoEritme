package com.blokoyun.uxmfarmerautosmelt;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class RuleConfiguration {
    static final String AUTO_PERMISSION = "uxmfarmerotosmelter.auto";
    static final String INSTANT_PERMISSION = "uxmfarmerotosmelter.instant";
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
        enabled = config.getBoolean("enabled", true);
        automatic = "automatic".equalsIgnoreCase(config.getString("trigger", "click"));
        autoSmeltEnabled = config.getBoolean("automatic-smelting.enabled", true);
        autoSmeltIntervalTicks = positiveTicks(config.getLong("automatic-smelting.interval-ticks", 20L));
        autoSmeltPermission = config.getString("automatic-smelting.permission", AUTO_PERMISSION).trim();
        instantSmeltPermission = config.getString("instant-smelting.permission", INSTANT_PERMISSION).trim();
        pluginName = config.getString("hook.plugin-name", "uxmFarmer");
        eventClassName = config.getString("hook.event-class",
                "xyz.geik.farmer.api.handlers.FarmerItemCollectEvent");
        priorityName = config.getString("hook.priority", "NORMAL");
        this.defaults = defaults;
        itemRules = Map.copyOf(rules);
        this.recipes = List.copyOf(recipes);
        guiSettings = gui;
    }

    static RuleConfiguration load(JavaPlugin plugin) {
        return load(plugin.getConfig(), plugin.getLogger());
    }

    static RuleConfiguration load(FileConfiguration config, java.util.logging.Logger logger) {
        GuiSettings gui;
        try {
            gui = GuiSettings.load(config);
        } catch (IllegalArgumentException ex) {
            logger.warning("gui bölümü geçersiz; güvenli varsayılanlar kullanılıyor: " + ex.getMessage());
            gui = GuiSettings.defaults();
        }
        ItemRule defaults;
        try {
            defaults = parseRule(config.getConfigurationSection("defaults"), null,
                    new ItemRule(null, ItemRule.RuleMode.PLUGIN, 1, 0, true, "", List.of()));
        } catch (IllegalArgumentException ex) {
            logger.warning("defaults bölümü geçersiz; güvenli plugin varsayılanları kullanılıyor: " + ex.getMessage());
            defaults = new ItemRule(null, ItemRule.RuleMode.PLUGIN, 1, 0, true, "", List.of());
        }
        Map<Material, ItemRule> rules = new EnumMap<>(Material.class);
        List<SmeltRecipe> recipes = new java.util.ArrayList<>();
        ConfigurationSection items = config.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                try {
                    ConfigurationSection section = items.getConfigurationSection(key);
                    if (material == null || !material.isItem() || section == null) {
                        throw new IllegalArgumentException("Geçersiz material veya kural bölümü");
                    }
                    ItemRule rule = parseRule(section, material, defaults);
                    Material output = SmeltRecipe.defaultOutput(material);
                    if (section.contains("output-material")) {
                        output = Material.matchMaterial(section.getString("output-material", ""));
                    }
                    if (rule.mode() == ItemRule.RuleMode.PLUGIN
                            && (output == null || !output.isItem() || output == material)) {
                        throw new IllegalArgumentException("plugin modu için geçerli, farklı output-material gerekli");
                    }
                    if (rule.mode() == ItemRule.RuleMode.COMMANDS && rule.commands().isEmpty()) {
                        throw new IllegalArgumentException("commands modu için boş olmayan komut listesi gerekli");
                    }
                    int seconds = section.getInt("duration-seconds", gui.durationSeconds());
                    int batch = section.getInt("batch-size", gui.batchSize());
                    rules.put(material, rule);
                    recipes.add(new SmeltRecipe(rule, output, seconds, batch));
                } catch (IllegalArgumentException ex) {
                    if (material != null) {
                        rules.remove(material);
                    }
                    logger.warning("Oto eritme kuralı atlandı [" + key + "]: " + ex.getMessage());
                }
            }
        }
        return new RuleConfiguration(config, defaults, rules, recipes, gui);
    }

    private static ItemRule parseRule(ConfigurationSection section, Material material, ItemRule fallback) {
        if (section == null) {
            return fallback.withMaterial(material);
        }
        String mode = section.getString("mode", fallback.mode().name());
        ItemRule.RuleMode parsed = ItemRule.RuleMode.valueOf(mode.trim().toUpperCase(java.util.Locale.ROOT));
        return new ItemRule(material, parsed, section.getInt("min-amount", fallback.minAmount()),
                section.getInt("max-amount", fallback.maxAmount()),
                section.getBoolean("consume", fallback.consume()),
                section.getString("permission", fallback.permission()),
                section.contains("commands") ? section.getStringList("commands") : fallback.commands());
    }

    private static long positiveTicks(long value) {
        return value > 0 ? value : 20L;
    }

    boolean enabled() { return enabled; }
    boolean automatic() { return automatic; }
    boolean autoSmeltEnabled() { return autoSmeltEnabled; }
    long autoSmeltIntervalTicks() { return autoSmeltIntervalTicks; }
    String autoSmeltPermission() { return autoSmeltPermission; }
    String instantSmeltPermission() { return instantSmeltPermission; }
    String pluginName() { return pluginName; }
    String eventClassName() { return eventClassName; }
    String priorityName() { return priorityName; }
    int itemCount() { return itemRules.size(); }
    ItemRule ruleFor(Material material) { return itemRules.getOrDefault(material, defaults.withMaterial(material)); }
    List<SmeltRecipe> recipes() { return recipes; }
    GuiSettings guiSettings() { return guiSettings; }

    record GuiSettings(boolean enabled, int slot, int rows, int durationSeconds, int batchSize,
                       String permission, Material buttonMaterial, String buttonName, List<String> buttonLore,
                       String progressName, int barLength, String title, Material fillerMaterial) {
        private static final String DEFAULT_TITLE = "<black><bold>Oto Eritme";
        private static final Material DEFAULT_FILLER = Material.GRAY_STAINED_GLASS_PANE;

        static GuiSettings defaults() {
            return new GuiSettings(true, 7, 6, 10, 64, "uxmfarmerotosmelter.use", Material.BLAST_FURNACE,
                    "<gold>Oto Eritme", List.of("<gray>Eritme menüsünü açmak için tıkla"),
                    "<gold>Eritiliyor", 20, DEFAULT_TITLE, DEFAULT_FILLER);
        }

        static GuiSettings load(FileConfiguration config) {
            int rows = config.getInt("gui.rows", 6);
            if (rows < 3 || rows > 6) {
                throw new IllegalArgumentException("gui.rows 3..6 arasında olmalı");
            }
            int slot = config.getInt("gui.slot", 7);
            if (slot < 0 || slot >= rows * 9) {
                throw new IllegalArgumentException("gui.slot menü boyutunun dışında");
            }
            Material icon = Material.matchMaterial(config.getString("gui.button.material", "BLAST_FURNACE"));
            if (icon == null || !icon.isItem() || icon.isAir()) {
                throw new IllegalArgumentException("gui.button.material geçersiz");
            }
            Material filler = Material.matchMaterial(config.getString("gui.filler-material",
                    DEFAULT_FILLER.name()));
            if (filler == null || !filler.isItem() || filler.isAir()) {
                throw new IllegalArgumentException("gui.filler-material geçersiz");
            }
            String buttonName = config.contains("gui.button.name")
                    ? config.getString("gui.button.name", "<gold>Oto Eritme")
                    : "<gold>Oto Eritme";
            List<String> buttonLore = config.contains("gui.button.lore")
                    ? List.copyOf(config.getStringList("gui.button.lore"))
                    : List.of("<gray>Eritme menüsünü açmak için tıkla");
            String progressName = config.contains("gui.progress.name")
                    ? config.getString("gui.progress.name", "<gold>Eritiliyor")
                    : "<gold>Eritiliyor";
            return new GuiSettings(config.getBoolean("gui.enabled", true), slot, rows,
                    config.getInt("gui.duration-seconds", 10), config.getInt("gui.batch-size", 64),
                    config.getString("gui.permission", "uxmfarmerotosmelter.use"), icon,
                    buttonName, buttonLore, progressName,
                    Math.max(1, Math.min(config.getInt("gui.progress.bar-length", 20), 50)),
                    config.getString("gui.title", DEFAULT_TITLE), filler);
        }
    }
}
