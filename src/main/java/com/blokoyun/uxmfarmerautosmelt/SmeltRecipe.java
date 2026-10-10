package com.blokoyun.uxmfarmerautosmelt;

import org.bukkit.Material;

record SmeltRecipe(ItemRule rule, Material output, int durationSeconds, int batchSize) {
    SmeltRecipe {
        if (durationSeconds < 1 || durationSeconds > 86400 || batchSize < 0) {
            throw new IllegalArgumentException("Süre 1..86400 saniye, batch-size 0 veya pozitif olmalı");
        }
    }

    int amountFor(int stock) {
        int amount = batchSize == 0 ? stock : Math.min(stock, batchSize);
        if (rule.maxAmount() > 0) {
            amount = Math.min(amount, rule.maxAmount());
        }
        return amount > 0 && rule.matches(amount) ? amount : 0;
    }

    static Material defaultOutput(Material input) {
        return switch (input) {
            case IRON_ORE, DEEPSLATE_IRON_ORE, RAW_IRON -> Material.IRON_INGOT;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE, RAW_GOLD -> Material.GOLD_INGOT;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE, RAW_COPPER -> Material.COPPER_INGOT;
            case ANCIENT_DEBRIS -> Material.NETHERITE_SCRAP;
            case COBBLESTONE -> Material.STONE;
            case SAND, RED_SAND -> Material.GLASS;
            default -> null;
        };
    }
}
