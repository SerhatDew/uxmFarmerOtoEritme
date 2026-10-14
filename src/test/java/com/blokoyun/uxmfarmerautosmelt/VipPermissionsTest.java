package com.blokoyun.uxmfarmerautosmelt;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VipPermissionsTest {
    @BeforeAll
    static void registries() throws Exception {
        PaperRegistryFixture.initialize();
    }

    @Test
    void instantPermissionAloneCannotEnableAutomaticProcessing() throws Exception {
        try (Fixture f = new Fixture()) {
            f.scan();
            assertNull(f.controller.operation(f.farmerId));
            f.permissions.add(RuleConfiguration.INSTANT_PERMISSION);
            f.scan();
            assertNull(f.controller.operation(f.farmerId));
            assertEquals(3, f.source.getAmount());
            assertEquals(2, f.output.getAmount());
        }
    }

    @Test
    void autoPermissionAloneRetainsTimerAndIsRecheckedBeforeCompletion() throws Exception {
        try (Fixture f = new Fixture()) {
            f.permissions.add(RuleConfiguration.AUTO_PERMISSION);
            f.scan();
            assertNotNull(f.controller.operation(f.farmerId));
            assertEquals(3, f.source.getAmount());
            f.permissions.remove(RuleConfiguration.AUTO_PERMISSION);
            f.now.set(10_000_000_000L);
            f.scan();
            assertNull(f.controller.operation(f.farmerId));
            assertEquals(3, f.source.getAmount());
            assertEquals(2, f.output.getAmount());
        }
    }

    @Test
    void bothVipPermissionsDepositImmediatelyAndNeverDuplicateOnNextScan() throws Exception {
        try (Fixture f = new Fixture()) {
            f.permissions.addAll(List.of(RuleConfiguration.AUTO_PERMISSION, RuleConfiguration.INSTANT_PERMISSION));
            f.scan();
            assertEquals(0, f.source.getAmount());
            assertEquals(5, f.output.getAmount());
            assertNull(f.controller.operation(f.farmerId));
            f.scan();
            assertEquals(0, f.source.getAmount());
            assertEquals(5, f.output.getAmount());
        }
    }

    @Test
    void instantProcessingStillRespectsCapacityAndRecipePermission() throws Exception {
        try (Fixture f = new Fixture("ore.vip")) {
            f.permissions.addAll(List.of(RuleConfiguration.AUTO_PERMISSION, RuleConfiguration.INSTANT_PERMISSION));
            f.scan();
            assertEquals(3, f.source.getAmount(), "VIP is not a recipe-permission bypass");
            f.permissions.add("ore.vip");
            f.output.setAmount(64);
            f.scan();
            assertEquals(3, f.source.getAmount());
            assertEquals(64, f.output.getAmount());
            f.output.setAmount(61);
            f.scan();
            assertEquals(0, f.source.getAmount());
            assertEquals(64, f.output.getAmount());
        }
    }

    @Test
    void offlineOwnerCannotUseVipAutomationEvenWithPreviouslyGrantedPermissions() throws Exception {
        try (Fixture f = new Fixture()) {
            f.permissions.addAll(List.of(RuleConfiguration.AUTO_PERMISSION, RuleConfiguration.INSTANT_PERMISSION));
            when(f.player.isOnline()).thenReturn(false);
            f.scan();
            assertEquals(3, f.source.getAmount());
            assertEquals(2, f.output.getAmount());
            when(f.player.isOnline()).thenReturn(true);
            f.scan();
            assertEquals(0, f.source.getAmount());
            assertEquals(5, f.output.getAmount());
        }
    }

    @Test
    void instantManualNeedsUseAndFarmerAccessButNotAutomaticPermission() throws Exception {
        try (Fixture f = new Fixture()) {
            f.permissions.add(RuleConfiguration.INSTANT_PERMISSION);
            f.controller.start(f.player, f.farmerId, f.recipe);
            assertEquals(3, f.source.getAmount(), "instant does not grant manual use permission");
            f.permissions.add(SmeltGuiHook.USE_PERMISSION);
            when(f.storage.canAccess(f.farmerId, f.player)).thenReturn(false);
            f.controller.start(f.player, f.farmerId, f.recipe);
            assertEquals(3, f.source.getAmount(), "instant does not grant access to another Farmer");
            when(f.storage.canAccess(f.farmerId, f.player)).thenReturn(true);
            f.controller.start(f.player, f.farmerId, f.recipe);
            assertEquals(0, f.source.getAmount());
            assertEquals(5, f.output.getAmount());
            assertNull(f.controller.operation(f.farmerId));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final UUID farmerId = UUID.randomUUID();
        final UUID owner = UUID.randomUUID();
        final Player player = mock(Player.class);
        final FarmerStorage storage = mock(FarmerStorage.class);
        final Set<String> permissions = new HashSet<>();
        final AtomicLong now = new AtomicLong();
        final SmeltControllerTest.FakeItem source = new SmeltControllerTest.FakeItem(3);
        final SmeltControllerTest.FakeItem output = new SmeltControllerTest.FakeItem(2);
        final MockedStatic<Bukkit> bukkit;
        final SmeltController controller;
        final SmeltRecipe recipe;
        Runnable ticker;

        Fixture() throws Exception { this(""); }

        Fixture(String recipePermission) throws Exception {
            JavaPlugin plugin = mock(JavaPlugin.class);
            Plugin dependency = mock(Plugin.class);
            PluginManager manager = mock(PluginManager.class);
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            when(dependency.isEnabled()).thenReturn(true);
            when(manager.getPlugin(anyString())).thenReturn(dependency);
            when(player.getUniqueId()).thenReturn(owner);
            when(player.getName()).thenReturn("vip-owner");
            when(player.isOnline()).thenReturn(true);
            when(player.hasPermission(anyString())).thenAnswer(call -> permissions.contains(call.getArgument(0)));
            var farmer = new SmeltControllerTest.FakeFarmer(owner, farmerId);
            when(storage.automaticAvailable()).thenReturn(true);
            when(storage.farmers()).thenAnswer(call -> List.of(farmer));
            when(storage.owner(farmerId)).thenReturn(owner);
            when(storage.canAccess(farmerId, player)).thenReturn(true);
            when(storage.amount(eq(farmerId), eq(Material.RAW_IRON))).thenAnswer(call -> source.getAmount());
            when(storage.snapshot(eq(farmerId), any(SmeltRecipe.class))).thenAnswer(call ->
                    new FarmerStorage.StockSnapshot(source, source.getAmount(), output, output.getAmount(), 64));
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("automatic-smelting.interval-ticks", 5);
            yaml.set("gui.enabled", false);
            yaml.set("items.RAW_IRON.mode", "plugin");
            yaml.set("items.RAW_IRON.output-material", "IRON_INGOT");
            yaml.set("items.RAW_IRON.permission", recipePermission);
            yaml.set("items.RAW_IRON.duration-seconds", 10);
            RuleConfiguration config = RuleConfiguration.load(yaml, Logger.getAnonymousLogger());
            recipe = config.recipes().getFirst();
            controller = new SmeltController(plugin, config, storage, now::get);
            bukkit = mockStatic(Bukkit.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getPlayer(owner)).thenReturn(player);
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(5L), eq(5L))).thenAnswer(call -> {
                ticker = call.getArgument(1);
                return mock(BukkitTask.class);
            });
            if (!controller.register()) throw new IllegalStateException("controller registration failed");
        }

        void scan() { ticker.run(); }

        @Override
        public void close() {
            try { controller.shutdown(); }
            finally { bukkit.close(); }
        }
    }
}
