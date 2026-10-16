package com.blokoyun.hooks.farmersmelt.config;

import com.blokoyun.hooks.farmersmelt.PaperRegistryFixture;
import org.junit.jupiter.api.BeforeAll;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigurationValidationTest {
    private static final Logger LOGGER = Logger.getAnonymousLogger();

    @BeforeAll
    static void registries() throws Exception {
        PaperRegistryFixture.initialize();
    }

    @Test
    void rejectsMalformedBooleanInsteadOfApplyingDefault() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", "yes");
        assertThrows(IllegalArgumentException.class, () -> RuleConfiguration.load(yaml, LOGGER));
    }

    @Test
    void rejectsMalformedNumberAndListTypes() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("automatic-smelting.interval-ticks", "20");
        assertThrows(IllegalArgumentException.class, () -> RuleConfiguration.load(yaml, LOGGER));

        YamlConfiguration commands = new YamlConfiguration();
        commands.set("defaults.commands", "give player iron_ingot");
        assertThrows(IllegalArgumentException.class, () -> RuleConfiguration.load(commands, LOGGER));
    }

    @Test
    void rejectsFutureSchema() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("config-version", 5);
        assertThrows(IllegalArgumentException.class, () -> RuleConfiguration.load(yaml, LOGGER));
    }

}
