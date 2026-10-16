package com.blokoyun.hooks.farmersmelt.listener;
import com.blokoyun.hooks.farmersmelt.config.*;
import com.blokoyun.hooks.farmersmelt.smelt.*;
import com.blokoyun.hooks.farmersmelt.integration.FarmerStorage;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import java.nio.charset.StandardCharsets;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SmeltGuiHookTest {
    @Test
    void modernMainMenuEventRemembersFarmerAndExpiresUnusedRequest() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SmeltGuiHook hook = new SmeltGuiHook(plugin, mock(RuleConfiguration.class), mock(SmeltController.class));
        set(hook, "menuEventClass", MainMenuEvent.class);
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            MainMenuEvent event = new MainMenuEvent(player, false);
            capture(hook, event);
            Map<?, ?> requests = (Map<?, ?>) field(hook, "pending");
            assertTrue(requests.containsKey(playerId));
            Object pending = requests.get(playerId);
            var farmerId = pending.getClass().getDeclaredMethod("farmerId");
            farmerId.setAccessible(true);
            assertEquals(event.getFarmerUUID(), farmerId.invoke(pending));
            var expiry = org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTaskLater(eq(plugin), expiry.capture(), eq(40L));
            expiry.getValue().run();
            assertTrue(requests.isEmpty());
        }
    }

    @Test
    void cancelledMainMenuCannotMarkAnotherInventoryForInjection() throws Exception {
        SmeltGuiHook hook = new SmeltGuiHook(mock(JavaPlugin.class), mock(RuleConfiguration.class), mock(SmeltController.class));
        set(hook, "menuEventClass", MainMenuEvent.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            capture(hook, new MainMenuEvent(mock(Player.class), true));
            assertTrue(((Map<?, ?>) field(hook, "pending")).isEmpty());
        }
    }

    @Test
    void legacyMenuBindsTheActualFarmerStorage() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Plugin dependency = mock(Plugin.class);
        when(dependency.isEnabled()).thenReturn(true);
        SmeltController controller = new SmeltController(plugin, mock(RuleConfiguration.class));
        set(controller, "storage", new FarmerStorage(dependency));
        SmeltGuiHook hook = new SmeltGuiHook(plugin, mock(RuleConfiguration.class), controller);
        set(hook, "menuEventClass", LegacyMenuEvent.class);
        LegacyFarmer farmer = new LegacyFarmer();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            capture(hook, new LegacyMenuEvent(mock(Player.class), farmer));
            UUID token = UUID.nameUUIDFromBytes("farmer:region-17".getBytes(StandardCharsets.UTF_8));
            assertEquals(7, controller.stock(token, Material.RAW_IRON));
        }
    }

    public static class LegacyFarmer {
        public String getRegionID() { return "region-17"; }
        public Map<Material, LegacyItem> getCollectedItems() { return Map.of(Material.RAW_IRON, new LegacyItem()); }
    }

    public static class LegacyItem {
        public int getAmount() { return 7; }
    }

    public static class LegacyMenuEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        private final Player player;
        private final LegacyFarmer farmer;
        LegacyMenuEvent(Player player, LegacyFarmer farmer) {
            this.player = player;
            this.farmer = farmer;
        }
        public Player getPlayer() { return player; }
        public LegacyFarmer getFarmer() { return farmer; }
        @Override public HandlerList getHandlers() { return HANDLERS; }
    }

    private static void capture(SmeltGuiHook hook, Event event) throws Exception {
        var bind = SmeltGuiHook.class.getDeclaredMethod("bindMenuMethods");
        bind.setAccessible(true);
        bind.invoke(hook);
        var method = SmeltGuiHook.class.getDeclaredMethod("captureFarmer", Event.class);
        method.setAccessible(true);
        method.invoke(hook, event);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    public static class MainMenuEvent extends Event implements Cancellable {
        private static final HandlerList HANDLERS = new HandlerList();
        private final UUID farmerId = UUID.randomUUID();
        private final Player player;
        private boolean cancelled;
        MainMenuEvent(Player player, boolean cancelled) {
            this.player = player;
            this.cancelled = cancelled;
        }
        public UUID getFarmerUUID() { return farmerId; }
        public Player getPlayer() { return player; }
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
        @Override public boolean isCancelled() { return cancelled; }
        @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    }
}
