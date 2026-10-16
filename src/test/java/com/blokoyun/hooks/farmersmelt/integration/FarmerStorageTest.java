package com.blokoyun.hooks.farmersmelt.integration;
import com.blokoyun.hooks.farmersmelt.smelt.*;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FarmerStorageTest {
    @ParameterizedTest
    @CsvSource({"RAW_IRON, IRON_INGOT", "IRON_ORE, IRON_INGOT", "GOLD_ORE, GOLD_INGOT"})
    void smeltedItemsAreAddedToSameFarmerWithoutCommands(Material source, Material output) throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        FarmerStorage storage = new FarmerStorage(plugin);
        UUID farmerId = new UUID(0, 1);
        Material otherSource = source == Material.RAW_IRON ? Material.IRON_ORE : Material.RAW_IRON;
        StoredItem sourceItem = new StoredItem(3);
        StoredItem ingots = new StoredItem(5);
        StoredItem otherItem = new StoredItem(17);
        storage.bind(farmerId, new StoredFarmer(Map.of(source, sourceItem, output, ingots, otherSource, otherItem)));
        ItemRule rule = new ItemRule(source, ItemRule.RuleMode.PLUGIN, 1, 0, false, "", List.of());
        SmeltRecipe recipe = new SmeltRecipe(rule, output, 10, 64);
        FarmerStorage.StockSnapshot snapshot = storage.snapshot(farmerId, recipe);
        assertEquals(3, sourceItem.getAmount(), "Bekleme süresinde kaynak tüketilmemeli");
        assertEquals(5, ingots.getAmount());
        assertEquals(SmeltTransaction.Result.COMPLETED, SmeltTransaction.complete(snapshot,
                rule.mode(), recipe.amountFor(snapshot.sourceAmount()), List.of(),
                command -> { throw new AssertionError("Envanter ödülü için komut çalıştırılmamalı"); }));
        assertEquals(0, storage.amount(farmerId, source));
        assertEquals(8, storage.amount(farmerId, output), "Mevcut külçeler korunarak aynı depoya eklenmeli");
        assertEquals(17, storage.amount(farmerId, otherSource));
    }

    @Test
    void insufficientIngotCapacityLeavesSourceAndDestinationUnchanged() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        FarmerStorage storage = new FarmerStorage(plugin);
        UUID farmerId = new UUID(0, 1);
        StoredItem source = new StoredItem(3);
        StoredItem output = new StoredItem(7);
        storage.bind(farmerId, new StoredFarmer(Map.of(Material.RAW_IRON, source, Material.IRON_INGOT, output)));
        ItemRule rule = new ItemRule(Material.RAW_IRON, ItemRule.RuleMode.PLUGIN, 1, 0, false, "", List.of());
        FarmerStorage.StockSnapshot snapshot = storage.snapshot(farmerId,
                new SmeltRecipe(rule, Material.IRON_INGOT, 10, 64));
        assertEquals(SmeltTransaction.Result.DESTINATION_FULL, SmeltTransaction.complete(snapshot,
                rule.mode(), 3, List.of(),
                command -> { throw new AssertionError("Komut çalıştırılmamalı"); }));
        assertEquals(3, source.getAmount());
        assertEquals(7, output.getAmount());
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
        public StoredLevel getCurrentLevel() { return new StoredLevel(); }
        public void setAmount(int amount) { this.amount = amount; }
    }

    public static class StoredLevel {
        public int getCapacity() { return 8; }
    }
}
