package com.blokoyun.uxmfarmerautosmelt;

import org.bukkit.Material;

import java.util.List;

/** Immutable rule applied to one UXMFarmer material. */
public record ItemRule(
        Material material,
        RuleMode mode,
        int minAmount,
        int maxAmount,
        boolean consume,
        String permission,
        List<String> commands
) {
    public ItemRule {
        if (minAmount < 0) {
            throw new IllegalArgumentException("minAmount cannot be negative");
        }
        if (maxAmount < 0) {
            throw new IllegalArgumentException("maxAmount cannot be negative");
        }
        commands = List.copyOf(commands);
        permission = permission == null ? "" : permission.trim();
    }

    public boolean matches(int amount) {
        return amount >= minAmount && (maxAmount == 0 || amount <= maxAmount);
    }

    ItemRule withMaterial(Material replacement) {
        return new ItemRule(replacement, mode, minAmount, maxAmount, consume, permission, commands);
    }

    public enum RuleMode {
        PLUGIN,
        COMMANDS;

        public static RuleMode from(String value, RuleMode fallback) {
            if (value == null) {
                return fallback;
            }
            try {
                return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }
}
