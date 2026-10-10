package com.blokoyun.uxmfarmerautosmelt;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class RuleConfiguration {
    private final boolean enabled;
    private final String pluginName;
    private final String eventClassName;
    private final String priorityName;
    private final ItemRule defaults;
    private final Map<Material, ItemRule> itemRules;

    private RuleConfiguration(
            boolean enabled,
            String pluginName,
            String eventClassName,
            String priorityName,
            ItemRule defaults,
            Map<Material, ItemRule> itemRules
    ) {
        this.enabled = enabled;
        this.pluginName = pluginName;
        this.eventClassName = eventClassName;
        this.priorityName = priorityName;
        this.defaults = defaults;
        this.itemRules = Map.copyOf(itemRules);
    }

    static RuleConfiguration load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection defaultsSection = config.getConfigurationSection("defaults");
        ItemRule defaults = parseRule(plugin, defaultsSection, null, ItemRule.RuleMode.PLUGIN, 1, 0, false, "", List.of());

        Map<Material, ItemRule> rules = new EnumMap<>(Material.class);
        ConfigurationSection items = config.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null) {
                    plugin.getLogger().warning("Bilinmeyen item adı config.yml items bölümünde yok sayıldı: " + key);
                    continue;
                }
                ConfigurationSection section = items.getConfigurationSection(key);
                rules.put(material, parseRule(plugin, section, material, defaults.mode(), defaults.minAmount(),
                        defaults.maxAmount(), defaults.consume(), defaults.permission(), defaults.commands()));
            }
        }

        return new RuleConfiguration(
                config.getBoolean("enabled", true),
                config.getString("hook.plugin-name", "uxmFarmer"),
                config.getString("hook.event-class", "com.uxplima.uxmfarmer.api.event.impl.FarmerCollectProductEvent"),
                config.getString("hook.priority", "NORMAL"),
                defaults,
                rules
        );
    }

    private static ItemRule parseRule(
            JavaPlugin plugin,
            ConfigurationSection section,
            Material material,
            ItemRule.RuleMode fallbackMode,
            int fallbackMin,
            int fallbackMax,
            boolean fallbackConsume,
            String fallbackPermission,
            List<String> fallbackCommands
    ) {
        String modeValue = section == null ? null : section.getString("mode");
        ItemRule.RuleMode mode = ItemRule.RuleMode.from(modeValue, fallbackMode);
        int min = section == null ? fallbackMin : section.getInt("min-amount", fallbackMin);
        int max = section == null ? fallbackMax : section.getInt("max-amount", fallbackMax);
        boolean consume = section == null ? fallbackConsume : section.getBoolean("consume", fallbackConsume);
        String permission = section == null ? fallbackPermission : section.getString("permission", fallbackPermission);
        List<String> commands = section == null ? fallbackCommands : section.getStringList("commands");
        if (section != null && !section.contains("commands")) {
            commands = fallbackCommands;
        }
        if (min < 0) {
            plugin.getLogger().warning("Negatif min-amount değeri 0 olarak düzeltildi: " + material);
            min = 0;
        }
        if (max < 0) {
            plugin.getLogger().warning("Negatif max-amount değeri sınırsız olarak düzeltildi: " + material);
            max = 0;
        }
        return new ItemRule(material, mode, min, max, consume, permission, new ArrayList<>(commands));
    }

    boolean enabled() {
        return enabled;
    }

    String pluginName() {
        return pluginName;
    }

    String eventClassName() {
        return eventClassName;
    }

    String priorityName() {
        return priorityName;
    }

    ItemRule ruleFor(Material material) {
        ItemRule rule = itemRules.get(material);
        return rule == null ? defaults.withMaterial(material) : rule;
    }

    int itemCount() {
        return itemRules.size();
    }
}
