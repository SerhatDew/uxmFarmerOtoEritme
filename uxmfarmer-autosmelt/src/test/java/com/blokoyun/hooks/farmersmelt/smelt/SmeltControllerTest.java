package com.blokoyun.hooks.farmersmelt.smelt;
import com.blokoyun.hooks.farmersmelt.config.*;
import com.blokoyun.hooks.farmersmelt.integration.*;
import com.blokoyun.hooks.farmersmelt.PaperRegistryFixture;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SmeltControllerTest {
    @BeforeAll
    static void initializePaperRegistries() throws ClassNotFoundException {
        PaperRegistryFixture.initialize();
    }

    @Test
    void timedManualOperationDepositsIntoFarmerAndSharesFarmerLock() {
        UUID owner = UUID.randomUUID();
        UUID farmerId = UUID.randomUUID();
        UUID secondFarmerId = UUID.randomUUID();
        FakeFarmer farmer = farmer(owner, 3, 2);
        FakeFarmer secondFarmer = farmer(owner, 3, 0);
        JavaPlugin plugin = mock(JavaPlugin.class);
        Plugin dependency = mock(Plugin.class);
        PluginManager manager = mock(PluginManager.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BossBar bossBar = mock(BossBar.class);
        Player player = mock(Player.class);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(dependency.isEnabled()).thenReturn(true);
        when(dependency.getName()).thenReturn("uxmFarmer");
        when(manager.getPlugin(anyString())).thenReturn(dependency);
        when(player.getUniqueId()).thenReturn(owner);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission(anyString())).thenReturn(true);
        when(player.hasPermission(RuleConfiguration.INSTANT_PERMISSION)).thenReturn(false);
        when(player.getName()).thenReturn("owner");

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", true);
        yaml.set("automatic-smelting.enabled", false);
        yaml.set("gui.permission", "");
        yaml.set("items.RAW_IRON.mode", "plugin");
        yaml.set("items.RAW_IRON.output-material", "IRON_INGOT");
        yaml.set("items.RAW_IRON.duration-seconds", 10);
        RuleConfiguration configuration = RuleConfiguration.load(yaml, Logger.getAnonymousLogger());
        AtomicLong now = new AtomicLong();
        SmeltController controller = new SmeltController(plugin, configuration, now::get);
        ArgumentCaptor<Runnable> ticker = ArgumentCaptor.forClass(Runnable.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
                    .thenReturn(bossBar);
            bukkit.when(() -> Bukkit.getPlayer(owner)).thenReturn(player);
            when(scheduler.runTaskTimer(eq(plugin), ticker.capture(), eq(5L), eq(5L))).thenReturn(mock(org.bukkit.scheduler.BukkitTask.class));

            assertTrue(controller.register());
            controller.bindFarmer(farmerId, farmer);
            controller.bindFarmer(secondFarmerId, secondFarmer);
            SmeltRecipe recipe = configuration.recipes().getFirst();

            controller.start(player, farmerId, recipe);
            assertNotNull(controller.operation(farmerId));
            controller.start(player, secondFarmerId, recipe);
            assertNull(controller.operation(secondFarmerId), "A player cannot run two manual jobs");

            now.set(9_999_999_999L);
            ticker.getValue().run();
            assertEquals(3, farmer.item(Material.RAW_IRON).getAmount());
            now.set(10_000_000_000L);
            ticker.getValue().run();
            assertNull(controller.operation(farmerId));
            assertEquals(0, farmer.item(Material.RAW_IRON).getAmount());
            assertEquals(5, farmer.item(Material.IRON_INGOT).getAmount());
            controller.shutdown();
        }
    }

    @Test
    void automaticScanSmeltsWithoutGuiAndRotatesRecipesFairly() throws Exception {
        UUID farmerId = UUID.randomUUID();
        FakeFarmer farmer = farmer(UUID.randomUUID(), farmerId, 2, 0, 1, 1);
        JavaPlugin plugin = mock(JavaPlugin.class);
        Plugin dependency = mock(Plugin.class);
        PluginManager manager = mock(PluginManager.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        FarmerStorage adapter = mock(FarmerStorage.class);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(dependency.isEnabled()).thenReturn(true);
        when(manager.getPlugin(anyString())).thenReturn(dependency);
        when(adapter.automaticAvailable()).thenReturn(true);
        when(adapter.farmers()).thenAnswer(invocation -> java.util.List.of(farmer));
        when(adapter.amount(eq(farmerId), eq(Material.RAW_IRON))).thenReturn(2);
        when(adapter.amount(eq(farmerId), eq(Material.RAW_GOLD))).thenReturn(1);
        AtomicLong now = new AtomicLong();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", true);
        yaml.set("automatic-smelting.enabled", true);
        yaml.set("automatic-smelting.interval-ticks", 5);
        yaml.set("automatic-smelting.permission", "");
        yaml.set("gui.enabled", false);
        yaml.set("items.RAW_IRON.mode", "plugin");
        yaml.set("items.RAW_IRON.output-material", "IRON_INGOT");
        yaml.set("items.RAW_IRON.duration-seconds", 1);
        yaml.set("items.RAW_GOLD.mode", "plugin");
        yaml.set("items.RAW_GOLD.output-material", "GOLD_INGOT");
        yaml.set("items.RAW_GOLD.duration-seconds", 1);
        RuleConfiguration configuration = RuleConfiguration.load(yaml, Logger.getAnonymousLogger());
        Map<Material, FakeItem> sources = farmer.items;
        when(adapter.snapshot(eq(farmerId), any(SmeltRecipe.class))).thenAnswer(invocation -> {
            SmeltRecipe recipe = invocation.getArgument(1);
            Material source = recipe.rule().material();
            Material output = recipe.output();
            return new FarmerStorage.StockSnapshot(sources.get(source), sources.get(source).getAmount(),
                    sources.get(output), sources.get(output).getAmount(), 64);
        });
        SmeltController controller = new SmeltController(plugin, configuration, adapter, now::get);
        ArgumentCaptor<Runnable> ticker = ArgumentCaptor.forClass(Runnable.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTaskTimer(eq(plugin), ticker.capture(), eq(5L), eq(5L)))
                    .thenReturn(mock(org.bukkit.scheduler.BukkitTask.class));
            assertTrue(controller.register());
            ticker.getValue().run();
            assertNotNull(controller.operation(farmerId));
            assertEquals(Material.RAW_IRON, controller.operation(farmerId).recipe().rule().material());
            assertEquals(2, sources.get(Material.RAW_IRON).getAmount());
            sources.get(Material.RAW_IRON).setAmount(4);
            now.set(1_000_000_000L);
            ticker.getValue().run();
            assertNotNull(controller.operation(farmerId));
            assertEquals(Material.RAW_GOLD, controller.operation(farmerId).recipe().rule().material(),
                    "the next configured ore gets a turn instead of iron monopolizing the farmer");
            assertEquals(2, sources.get(Material.RAW_IRON).getAmount());
            assertEquals(2, sources.get(Material.IRON_INGOT).getAmount());
            sources.get(Material.RAW_IRON).setAmount(0);
            now.set(2_000_000_000L);
            ticker.getValue().run();
            assertNull(controller.operation(farmerId));
            assertEquals(2, sources.get(Material.IRON_INGOT).getAmount());
            assertEquals(2, sources.get(Material.GOLD_INGOT).getAmount());
            controller.shutdown();
        }
    }

    @Test
    void automaticPermissionIsRecheckedBeforeDeposit() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID farmerId = UUID.randomUUID();
        FakeFarmer farmer = farmer(owner, farmerId, 1, 0, 0, 0);
        JavaPlugin plugin = mock(JavaPlugin.class);
        Plugin dependency = mock(Plugin.class);
        PluginManager manager = mock(PluginManager.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        FarmerStorage adapter = mock(FarmerStorage.class);
        Player player = mock(Player.class);
        FakeItem source = farmer.item(Material.RAW_IRON);
        FakeItem output = farmer.item(Material.IRON_INGOT);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(dependency.isEnabled()).thenReturn(true);
        when(manager.getPlugin(anyString())).thenReturn(dependency);
        when(adapter.automaticAvailable()).thenReturn(true);
        when(adapter.farmers()).thenAnswer(invocation -> java.util.List.of(farmer));
        when(adapter.amount(farmerId, Material.RAW_IRON)).thenReturn(1);
        when(adapter.snapshot(eq(farmerId), any(SmeltRecipe.class)))
                .thenReturn(new FarmerStorage.StockSnapshot(source, 1, output, 0, 64));
        when(adapter.owner(farmerId)).thenReturn(owner);
        when(adapter.canAccess(farmerId, player)).thenReturn(true);
        when(player.getUniqueId()).thenReturn(owner);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("smelt.raw")).thenReturn(true, false);
        when(player.hasPermission(RuleConfiguration.AUTO_PERMISSION)).thenReturn(true);
        AtomicLong now = new AtomicLong();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", true);
        yaml.set("automatic-smelting.enabled", true);
        yaml.set("automatic-smelting.interval-ticks", 5);
        yaml.set("items.RAW_IRON.mode", "plugin");
        yaml.set("items.RAW_IRON.output-material", "IRON_INGOT");
        yaml.set("items.RAW_IRON.permission", "smelt.raw");
        yaml.set("items.RAW_IRON.duration-seconds", 1);
        RuleConfiguration configuration = RuleConfiguration.load(yaml, Logger.getAnonymousLogger());
        SmeltController controller = new SmeltController(plugin, configuration, adapter, now::get);
        ArgumentCaptor<Runnable> ticker = ArgumentCaptor.forClass(Runnable.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getPlayer(owner)).thenReturn(player);
            when(scheduler.runTaskTimer(eq(plugin), ticker.capture(), eq(5L), eq(5L)))
                    .thenReturn(mock(org.bukkit.scheduler.BukkitTask.class));
            assertTrue(controller.register());
            ticker.getValue().run();
            assertNotNull(controller.operation(farmerId));
            now.set(1_000_000_000L);
            ticker.getValue().run();
        }
        assertEquals(1, source.getAmount(), "permission revocation cannot consume source");
        assertEquals(0, output.getAmount(), "permission revocation cannot deposit output");
    }

    @Test
    void cancelPlayerRemovesOnlyManualOperation() throws Exception {
        UUID automaticFarmer = UUID.randomUUID();
        UUID manualFarmer = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        SmeltRecipe recipe = new SmeltRecipe(
                new ItemRule(Material.RAW_IRON, ItemRule.RuleMode.PLUGIN, 1, 0, false, "", java.util.List.of()),
                Material.IRON_INGOT, 10, 64);
        JavaPlugin plugin = mock(JavaPlugin.class);
        RuleConfiguration configuration = RuleConfiguration.load(new YamlConfiguration(), Logger.getAnonymousLogger());
        SmeltController controller = new SmeltController(plugin, configuration);
        SmeltController.Operation automatic = new SmeltController.Operation(null, automaticFarmer, recipe, 1, 0, null);
        SmeltController.Operation manual = new SmeltController.Operation(playerId, manualFarmer, recipe, 1, 0, null);
        var field = SmeltController.class.getDeclaredField("operations");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, SmeltController.Operation> operations = (Map<UUID, SmeltController.Operation>) field.get(controller);
        operations.put(automaticFarmer, automatic);
        operations.put(manualFarmer, manual);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(null);
            controller.cancelPlayer(playerId);
        }
        assertSame(automatic, controller.operation(automaticFarmer));
        assertNull(controller.operation(manualFarmer));
    }

    private static FakeFarmer farmer(UUID owner, int rawIron, int ingots) {
        return farmer(owner, UUID.randomUUID(), rawIron, ingots, 0, 0);
    }

    private static FakeFarmer farmer(UUID owner, UUID id, int rawIron, int ironIngots,
                                     int rawGold, int goldIngots) {
        FakeFarmer farmer = new FakeFarmer(owner, id);
        farmer.items.put(Material.RAW_IRON, new FakeItem(rawIron));
        farmer.items.put(Material.IRON_INGOT, new FakeItem(ironIngots));
        farmer.items.put(Material.RAW_GOLD, new FakeItem(rawGold));
        farmer.items.put(Material.GOLD_INGOT, new FakeItem(goldIngots));
        return farmer;
    }

    public static final class FakeFarmer {
        private final UUID owner;
        private final UUID id;
        private final Map<Material, FakeItem> items = new HashMap<>();

        FakeFarmer(UUID owner, UUID id) {
            this.owner = owner;
            this.id = id;
        }

        public UUID getId() {
            return id;
        }

        public UUID getOwnerUUID() {
            return owner;
        }

        public boolean isMember(Player player) {
            return player != null && owner.equals(player.getUniqueId());
        }

        public Map<Material, FakeItem> getCollectedItems() {
            return items;
        }

        FakeItem item(Material material) {
            return items.get(material);
        }
    }

    public static final class FakeItem {
        private int amount;

        FakeItem(int amount) {
            this.amount = amount;
        }

        public int getAmount() {
            return amount;
        }

        public boolean isLocked() {
            return false;
        }

        public Level getCurrentLevel() {
            return new Level();
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }
    }

    public static final class Level {
        public int getCapacity() {
            return 64;
        }
    }
}
