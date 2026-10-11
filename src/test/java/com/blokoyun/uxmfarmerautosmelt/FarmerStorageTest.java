package com.blokoyun.uxmfarmerautosmelt;

import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FarmerStorageTest {
    @Test
    void threeRawIronAreReadAndConsumedAsThreeIngots() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        FarmerStorage storage = new FarmerStorage(plugin);
        UUID farmerId = UUID.randomUUID();
        StoredItem item = new StoredItem(3);
        storage.bind(farmerId, new StoredFarmer(Map.of(Material.RAW_IRON, item)));
        assertEquals(0, storage.amount(farmerId, Material.IRON_ORE));
        assertEquals(3, storage.amount(farmerId, Material.RAW_IRON));
        ItemRule rule = new ItemRule(Material.RAW_IRON, ItemRule.RuleMode.COMMANDS,
                1, 0, true, "", List.of("give %player% iron_ingot %amount%"));
        SmeltRecipe recipe = new SmeltRecipe(rule, Material.IRON_INGOT, 10, 64);
        FarmerStorage.StockSnapshot snapshot = storage.snapshot(farmerId, recipe);
        int amount = recipe.amountFor(snapshot.sourceAmount());
        assertEquals(3, amount);
        String command = CommandContext.of(Material.RAW_IRON, amount, "Oyuncu", "", farmerId.toString())
                .apply(rule.commands().getFirst());
        assertEquals(SmeltTransaction.Result.COMPLETED, SmeltTransaction.complete(snapshot,
                rule.mode(), amount, List.of(command), actual -> {
                    assertEquals(0, item.getAmount(), "Stok ödülden önce tüketilmeli");
                    assertEquals("give Oyuncu iron_ingot 3", actual);
                    return true;
                }));
        assertEquals(0, item.getAmount());
    }

    public static class StoredFarmer {
        private final Map<Material, StoredItem> items;
        StoredFarmer(Map<Material, StoredItem> items) { this.items = items; }
        public Map<Material, StoredItem> getCollectedItems() { return items; }
    }

    public static class StoredItem {
        private int amount;
        StoredItem(int amount) { this.amount = amount; }
        public int getAmount() { return amount; }
        public boolean isLocked() { return false; }
        public void setAmount(int amount) { this.amount = amount; }
    }
}
