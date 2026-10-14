package com.blokoyun.uxmfarmerautosmelt;

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

    private static void capture(SmeltGuiHook hook, Event event) throws Exception {
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
