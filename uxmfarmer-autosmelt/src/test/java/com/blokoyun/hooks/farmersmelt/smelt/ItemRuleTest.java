package com.blokoyun.hooks.farmersmelt.smelt;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemRuleTest {
    @Test
    void matchesConfiguredAmountRange() {
        ItemRule rule = new ItemRule(Material.IRON_ORE, ItemRule.RuleMode.COMMANDS, 8, 32, true, "", List.of("give %player% iron_ingot %amount%"));

        assertFalse(rule.matches(7));
        assertTrue(rule.matches(8));
        assertTrue(rule.matches(32));
        assertFalse(rule.matches(33));
    }

    @Test
    void zeroMaximumMeansUnlimited() {
        ItemRule rule = new ItemRule(Material.GOLD_ORE, ItemRule.RuleMode.COMMANDS, 1, 0, true, "", List.of("give {player} gold_ingot {amount}"));

        assertTrue(rule.matches(Integer.MAX_VALUE));
    }

    @Test
    void commandContextReplacesPercentAndBracePlaceholders() {
        UUID farmer = UUID.randomUUID();
        CommandContext context = CommandContext.of(Material.IRON_ORE, 12, "Oyuncu", farmer.toString(), farmer.toString());

        assertEquals("give Oyuncu IRON_ORE 12", context.apply("give %player% %item% 12"));
        assertEquals("give Oyuncu gold_ingot 12", context.apply("give {owner} gold_ingot {amount}"));
        assertEquals(farmer + " " + farmer, context.apply("%uuid% %farmer_uuid%"));
    }
}
