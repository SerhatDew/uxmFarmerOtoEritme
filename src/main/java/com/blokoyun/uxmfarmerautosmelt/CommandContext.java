package com.blokoyun.uxmfarmerautosmelt;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

final class CommandContext {
    private final Map<String, String> values;

    private CommandContext(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    static CommandContext of(Material material, int amount, String ownerName, String ownerUuid, String farmerUuid) {
        Map<String, String> values = new HashMap<>();
        values.put("player", ownerName);
        values.put("owner", ownerName);
        values.put("uuid", ownerUuid);
        values.put("item", material.name());
        values.put("amount", Integer.toString(amount));
        values.put("farmer_uuid", farmerUuid);
        return new CommandContext(values);
    }

    String apply(String template) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
