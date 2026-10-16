package com.blokoyun.hooks.farmersmelt.integration;

import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FarmerReflectionTest {
    private JavaPlugin plugin() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        return plugin;
    }

    @Test void modernBindingUpdatesTheRemainingCollectedAmount() throws Exception {
        var binding = FarmerReflection.bind(ModernEvent.class, plugin(), null);
        ModernEvent event = new ModernEvent();
        var data = binding.readEvent(event);
        assertEquals(Material.RAW_IRON, data.material());
        assertEquals(event.getFarmerUUID(), data.farmerUuid());
        assertEquals(8, data.amount());
        data.setAmount(event, 3);
        assertEquals(3, event.getCollectedAmount());
    }

    @Test void legacyBindingReadsOwnerAndCancelsConsumedCollection() throws Exception {
        var binding = FarmerReflection.bind(LegacyEvent.class, plugin(), null);
        LegacyEvent event = new LegacyEvent();
        when(event.item.getType()).thenReturn(Material.RAW_GOLD);
        var data = binding.readEvent(event);
        assertEquals(Material.RAW_GOLD, data.material());
        assertEquals(event.farmer.getOwnerUUID(), data.farmerUuid());
        assertEquals(5, data.amount());
        data.setAmount(event, 0);
        assertTrue(event.isCancelled());
    }

    @Test void unsupportedEventContractFailsDuringBinding() {
        assertThrows(NoSuchMethodException.class, () -> FarmerReflection.bind(UnsupportedEvent.class, plugin(), null));
    }

    public static class ModernEvent extends Event {
        private final UUID farmer = UUID.randomUUID();
        private int amount = 8;
        public Material getCollectedProduct() { return Material.RAW_IRON; }
        public UUID getFarmerUUID() { return farmer; }
        public int getCollectedAmount() { return amount; }
        public void setCollectedAmount(int amount) { this.amount = amount; }
        @Override public HandlerList getHandlers() { return new HandlerList(); }
    }

    public static class LegacyFarmer {
        private final UUID owner = UUID.randomUUID();
        public UUID getOwnerUUID() { return owner; }
    }

    public static class LegacyEvent extends Event implements Cancellable {
        private final LegacyFarmer farmer = new LegacyFarmer();
        private final ItemStack item = mock(ItemStack.class);
        private boolean cancelled;
        public LegacyFarmer getFarmer() { return farmer; }
        public ItemStack getItem() { return item; }
        public int getCollectAmount() { return 5; }
        @Override public boolean isCancelled() { return cancelled; }
        @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
        @Override public HandlerList getHandlers() { return new HandlerList(); }
    }

    public static class UnsupportedEvent extends Event {
        @Override public HandlerList getHandlers() { return new HandlerList(); }
    }
}
