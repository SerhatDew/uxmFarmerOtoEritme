package com.blokoyun.uxmfarmerautosmelt;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.logging.Level;

/** Owns timed manual jobs and the independent Farmer-storage smelting loop. */
final class SmeltController implements Listener {
    private static final long NANOSECONDS_PER_SECOND = 1_000_000_000L;
    private static final long TICK_PERIOD = 5L;
    private static final Set<Material> AUTOMATIC_ORES = Set.of(
            Material.RAW_IRON, Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.RAW_GOLD, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.NETHER_GOLD_ORE, Material.RAW_COPPER, Material.COPPER_ORE,
            Material.DEEPSLATE_COPPER_ORE);

    private final JavaPlugin plugin;
    private final RuleConfiguration configuration;
    private final FarmerStorage storageOverride;
    private final LongSupplier clock;
    private final Map<UUID, Operation> operations = new HashMap<>();
    private final Map<UUID, Integer> recipeCursors = new HashMap<>();
    private final Map<UUID, Object> pendingFarmers = new HashMap<>();
    private final Set<UUID> warnedFarmers = new HashSet<>();
    private FarmerStorage storage;
    private Plugin dependency;
    private BukkitTask ticker;
    private long scanTicks;
    private boolean registered;
    private boolean automaticAvailable;
    private boolean automaticWarningLogged;
    private boolean dependencyWarningLogged;

    SmeltController(JavaPlugin plugin, RuleConfiguration configuration) {
        this(plugin, configuration, null, System::nanoTime);
    }

    SmeltController(JavaPlugin plugin, RuleConfiguration configuration, LongSupplier clock) {
        this(plugin, configuration, null, clock);
    }

    SmeltController(JavaPlugin plugin, RuleConfiguration configuration, FarmerStorage storage,
                    LongSupplier clock) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.storageOverride = storage;
        this.clock = clock;
    }

    boolean register() {
        if (registered) return true;
        if (!configuration.enabled()) return true;
        dependency = FarmerDependency.find(Bukkit.getPluginManager(), configuration.pluginName());
        if (dependency == null || !dependency.isEnabled()) {
            if (!dependencyWarningLogged) {
                plugin.getLogger().warning("Farmer depolama bağlantısı kurulamadı: Farmer eklentisi bulunamadı veya etkin değil.");
                dependencyWarningLogged = true;
            }
            return false;
        }
        dependencyWarningLogged = false;
        storage = storageOverride != null ? storageOverride : new FarmerStorage(dependency);
        for (Map.Entry<UUID, Object> entry : pendingFarmers.entrySet()) {
            storage.bind(entry.getKey(), entry.getValue());
        }
        pendingFarmers.clear();
        automaticAvailable = storage.automaticAvailable();
        if (configuration.autoSmeltEnabled() && !automaticAvailable && !automaticWarningLogged) {
            plugin.getLogger().warning("Otomatik eritme kullanılamıyor: yalnızca modern UXMFarmer FarmerRepository API destekleniyor; manuel GUI korunuyor.");
            automaticWarningLogged = true;
        }
        try {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
            registered = true;
            return true;
        } catch (RuntimeException | LinkageError ex) {
            HandlerList.unregisterAll(this);
            plugin.getLogger().log(Level.WARNING, "Farmer eritme denetleyicisi kurulamadı", ex);
            return false;
        }
    }

    boolean isRegistered() {
        return registered;
    }

    boolean automaticAvailable() {
        return automaticAvailable;
    }

    void shutdown() {
        if (ticker != null) ticker.cancel();
        ticker = null;
        HandlerList.unregisterAll(this);
        for (Operation operation : operations.values()) {
            if (operation.bar() != null) operation.bar().removeAll();
        }
        operations.clear();
        recipeCursors.clear();
        warnedFarmers.clear();
        scanTicks = 0;
        registered = false;
        automaticAvailable = false;
        storage = null;
        dependency = null;
        pendingFarmers.clear();
    }

    void bindFarmer(UUID farmerId, Object farmer) {
        if (farmerId == null || farmer == null) return;
        if (storage == null) pendingFarmers.put(farmerId, farmer);
        else storage.bind(farmerId, farmer);
    }

    int stock(UUID farmerId, Material material) throws ReflectiveOperationException {
        ensureStorage();
        return storage.amount(farmerId, material);
    }

    FarmerStorage.StockSnapshot snapshot(UUID farmerId, SmeltRecipe recipe) throws ReflectiveOperationException {
        ensureStorage();
        return storage.snapshot(farmerId, recipe);
    }

    boolean authorized(Player player, UUID farmerId, ItemRule rule) {
        if (player == null || farmerId == null) return false;
        String extra = configuration.guiSettings().permission();
        if (extra == null) extra = "";
        if (!player.hasPermission(SmeltGuiHook.USE_PERMISSION)
                || !extra.isBlank() && !player.hasPermission(extra)
                || rule != null && !rule.permission().isBlank() && !player.hasPermission(rule.permission())) {
            PlayerMessages.send(player, "§cᴏᴛᴏ ᴇʀɪᴛᴍᴇ ʏᴇᴛᴋɪɴ ʏᴏᴋ.");
            return false;
        }
        try {
            ensureStorage();
            if (!storage.canAccess(farmerId, player)) {
                PlayerMessages.send(player, "§cʏᴀʟɴɪᴢᴄᴀ ᴋᴇɴᴅɪ ᴄɪꜰᴛᴄɪɴᴅᴇ ᴇʀɪᴛᴍᴇ ʏᴀᴘᴀʙɪʟɪʀsɪɴ.");
                return false;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            PlayerMessages.send(player, "§cᴄɪꜰᴛɪ ᴅᴇᴘᴏsᴜɴᴀ ᴜʟᴀsɪʟᴀᴍᴀᴅɪ.");
            plugin.getLogger().log(Level.WARNING, "Farmer erişimi doğrulanamadı", ex);
            return false;
        }
    }

    void start(Player player, UUID farmerId, SmeltRecipe recipe) {
        if (!authorized(player, farmerId, recipe.rule())) return;
        if (operations.containsKey(farmerId) || hasPlayerOperation(player.getUniqueId())) {
            PlayerMessages.send(player, "§eʙɪʀ ᴇʀɪᴛᴍᴇ ɪsʟᴇᴍɪ ᴢᴀᴛᴇɴ ᴅᴇᴠᴀᴍ ᴇᴅɪʏᴏʀ.");
            return;
        }
        try {
            FarmerStorage.StockSnapshot snapshot = snapshot(farmerId, recipe);
            int amount = recipe.amountFor(snapshot.sourceAmount());
            if (amount == 0 || recipe.rule().mode() == ItemRule.RuleMode.PLUGIN
                    && snapshot.destinationSpace() < amount) {
                PlayerMessages.send(player, "§cᴅᴇᴘᴏ ᴠᴇʏᴀ ᴍɪᴋᴛᴀʀ sᴀʀᴛɪ ᴜʏɢᴜɴ ᴅᴇɢɪʟ.");
                return;
            }
            BossBar bar = Bukkit.createBossBar("Eritiliyor", BarColor.YELLOW, BarStyle.SOLID);
            bar.setProgress(0.0);
            bar.addPlayer(player);
            operations.put(farmerId, new Operation(player.getUniqueId(), farmerId, recipe, amount, clock.getAsLong(), bar));
            PlayerMessages.send(player, "§eᴇʀɪᴛᴍᴇ ʙᴀsʟᴀᴅɪ: §f" + recipe.durationSeconds() + " s");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            PlayerMessages.send(player, "§cᴇʀɪᴛᴍᴇ ʙᴀsʟᴀᴛɪʟᴀᴍᴀᴅɪ.");
            plugin.getLogger().log(Level.WARNING, "Farmer eritme başlatılamadı", ex);
        }
    }

    void cancelPlayer(UUID playerId) {
        if (playerId == null) return;
        Iterator<Map.Entry<UUID, Operation>> iterator = operations.entrySet().iterator();
        while (iterator.hasNext()) {
            Operation operation = iterator.next().getValue();
            if (!playerId.equals(operation.playerId())) continue;
            iterator.remove();
            removeBar(operation);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                PlayerMessages.send(player, "§eɪsʟᴇᴍ ɪᴘᴛᴀʟ. ᴜʀᴜɴʟᴇʀ ᴅᴇᴘᴏᴅᴀ ᴋᴀʟᴅɪ.");
            }
            return;
        }
    }

    Operation operation(UUID farmerId) {
        return operations.get(farmerId);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelPlayer(event.getPlayer().getUniqueId());
    }

    private void tick() {
        if (!registered) return;
        long now = clock.getAsLong();
        Iterator<Map.Entry<UUID, Operation>> iterator = operations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Operation> entry = iterator.next();
            Operation operation = entry.getValue();
            if (!operation.automatic()) {
                Player player = Bukkit.getPlayer(operation.playerId());
                if (player == null || !player.isOnline()) {
                    iterator.remove();
                    removeBar(operation);
                    continue;
                }
                double progress = progress(operation, now);
                if (operation.bar() != null) {
                    operation.bar().setProgress(progress);
                    operation.bar().setTitle("Eritiliyor §f" + (int) (progress * 100) + "%");
                }
                if (progress >= 1.0) {
                    iterator.remove();
                    removeBar(operation);
                    completeManual(operation, player);
                }
            } else if (progress(operation, now) >= 1.0) {
                iterator.remove();
                removeBar(operation);
                completeAutomatic(operation);
            }
        }
        scanTicks += TICK_PERIOD;
        long interval = Math.max(1L, configuration.autoSmeltIntervalTicks());
        if (configuration.autoSmeltEnabled() && automaticAvailable && scanTicks >= interval) {
            scanTicks = 0;
            scanAutomatic();
        }
    }

    private void scanAutomatic() {
        try {
            for (Object farmer : storage.farmers()) {
                if (farmer == null) continue;
                UUID farmerId = (UUID) farmer.getClass().getMethod("getId").invoke(farmer);
                if (farmerId == null) continue;
                if (operations.containsKey(farmerId)) continue;
                Selection selection = chooseRecipe(farmerId, farmer);
                if (selection != null) startAutomatic(farmerId, selection);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            plugin.getLogger().log(Level.WARNING, "Farmer otomatik eritme taraması başarısız", ex);
        }
    }

    private Selection chooseRecipe(UUID farmerId, Object farmer) {
        List<SmeltRecipe> recipes = configuration.recipes();
        if (recipes.isEmpty()) return null;
        int start = Math.floorMod(recipeCursors.getOrDefault(farmerId, 0), recipes.size());
        for (int offset = 0; offset < recipes.size(); offset++) {
            int index = (start + offset) % recipes.size();
            SmeltRecipe recipe = recipes.get(index);
            if (!isAutomaticRecipe(recipe) || !automaticAllowed(farmerId, farmer, recipe.rule())) continue;
            try {
                FarmerStorage.StockSnapshot snapshot = snapshot(farmerId, recipe);
                int amount = recipe.amountFor(snapshot.sourceAmount());
                if (amount <= 0 || snapshot.destinationSpace() < amount) continue;
                recipeCursors.put(farmerId, (index + 1) % recipes.size());
                return new Selection(recipe, amount);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                warnFarmer(farmerId, "Farmer otomatik eritme stoğu okunamadı", ex);
            }
        }
        return null;
    }

    private boolean automaticAllowed(UUID farmerId, Object farmer, ItemRule rule) {
        if (rule.permission().isBlank()) return true;
        try {
            UUID owner = (UUID) farmer.getClass().getMethod("getOwnerUUID").invoke(farmer);
            Player player = owner == null ? null : Bukkit.getPlayer(owner);
            return player != null && player.isOnline() && automaticAuthorized(player, farmerId, rule);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            warnFarmer(farmerId, "Farmer otomatik eritme yetkisi okunamadı", ex);
            return false;
        }
    }

    private void startAutomatic(UUID farmerId, Selection selection) {
        operations.put(farmerId, new Operation(null, farmerId, selection.recipe(), selection.amount(), clock.getAsLong(), null));
    }


    private boolean automaticAuthorized(Player player, UUID farmerId, ItemRule rule) {
        if (!rule.permission().isBlank() && !player.hasPermission(rule.permission())) return false;
        try {
            return storage.canAccess(farmerId, player);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            warnFarmer(farmerId, "Farmer otomatik eritme sahibi doğrulanamadı", ex);
            return false;
        }
    }

    private void completeManual(Operation operation, Player player) {
        if (!authorized(player, operation.farmerId(), operation.recipe().rule())) return;
        try {
            FarmerStorage.StockSnapshot snapshot = snapshot(operation.farmerId(), operation.recipe());
            CommandContext context = CommandContext.of(operation.recipe().rule().material(), operation.amount(),
                    player.getName(), player.getUniqueId().toString(), operation.farmerId().toString());
            List<String> commands = operation.recipe().rule().commands().stream().map(context::apply).toList();
            SmeltTransaction.Result result = SmeltTransaction.complete(snapshot, operation.recipe().rule().mode(),
                    operation.amount(), commands,
                    command -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
            if (result == SmeltTransaction.Result.COMPLETED) {
                PlayerMessages.send(player, "§aᴇʀɪᴛᴍᴇ ᴛᴀᴍᴀᴍʟᴀɴᴅɪ.");
            } else if (result == SmeltTransaction.Result.COMMAND_FAILED) {
                PlayerMessages.send(player, "§cᴋᴏᴍᴜᴛ ᴜʏɢᴜʟᴀɴᴀᴍᴀᴅɪ; ᴛᴇᴋʀᴀʀ ᴜʀᴜɴ ᴠᴇʀɪʟᴍᴇᴅɪ.");
                plugin.getLogger().warning("Manual eritme komutu başarısız oldu; kaynak geri alınmadı: farmer=" + operation.farmerId());
            } else {
                PlayerMessages.send(player, "§cᴇʀɪᴛᴍᴇ ᴛᴀᴍᴀᴍʟᴀɴᴀᴍᴀᴅɪ; ᴋᴀʏɴᴀᴋ ᴅᴇᴘᴏᴅᴀ ᴋᴀʟᴅɪ.");
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            PlayerMessages.send(player, "§cᴇʀɪᴛᴍᴇ ɪsʟᴇᴍɪ ʙᴀsᴀʀɪsɪᴢ.");
            plugin.getLogger().log(Level.SEVERE, "Farmer eritme tamamlanamadı", ex);
        }
    }

    private void completeAutomatic(Operation operation) {
        if (!isAutomaticRecipe(operation.recipe()) || !automaticPermissionStillValid(operation)) return;
        try {
            FarmerStorage.StockSnapshot snapshot = snapshot(operation.farmerId(), operation.recipe());
            SmeltTransaction.Result result = SmeltTransaction.complete(snapshot, ItemRule.RuleMode.PLUGIN,
                    operation.amount(), List.of(), command -> false);
            if (result != SmeltTransaction.Result.COMPLETED && result != SmeltTransaction.Result.INSUFFICIENT_STOCK) {
                warnFarmer(operation.farmerId(), "Farmer otomatik eritme tamamlanamadı", null);
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            warnFarmer(operation.farmerId(), "Farmer otomatik eritme tamamlanamadı", ex);
        }
    }

    private boolean automaticPermissionStillValid(Operation operation) {
        ItemRule rule = operation.recipe().rule();
        if (rule.permission().isBlank()) return true;
        try {
            UUID owner = storage.owner(operation.farmerId());
            Player player = owner == null ? null : Bukkit.getPlayer(owner);
            return player != null && player.isOnline() && automaticAuthorized(player, operation.farmerId(), rule);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            warnFarmer(operation.farmerId(), "Farmer otomatik eritme sahibi doğrulanamadı", ex);
            return false;
        }
    }


    private boolean hasPlayerOperation(UUID playerId) {
        for (Operation operation : operations.values()) {
            if (playerId.equals(operation.playerId())) return true;
        }
        return false;
    }

    private void ensureStorage() {
        if (storage == null) throw new IllegalStateException("Farmer bağlantısı henüz kurulmadı");
    }

    private void removeBar(Operation operation) {
        if (operation.bar() != null) operation.bar().removeAll();
    }

    private void warnFarmer(UUID farmerId, String message, Throwable cause) {
        if (!warnedFarmers.add(farmerId)) return;
        if (cause == null) plugin.getLogger().warning(message + ": farmer=" + farmerId);
        else plugin.getLogger().log(Level.WARNING, message + ": farmer=" + farmerId, cause);
    }

    static boolean isAutomaticRecipe(SmeltRecipe recipe) {
        return recipe.rule().mode() == ItemRule.RuleMode.PLUGIN
                && AUTOMATIC_ORES.contains(recipe.rule().material())
                && recipe.output() != null;
    }

    private static double progress(Operation operation, long now) {
        long elapsed = Math.max(0L, now - operation.startedAt());
        long duration = Math.max(1L, operation.recipe().durationSeconds() * NANOSECONDS_PER_SECOND);
        return Math.min(1.0, elapsed / (double) duration);
    }

    private record Selection(SmeltRecipe recipe, int amount) { }

    record Operation(UUID playerId, UUID farmerId, SmeltRecipe recipe, int amount, long startedAt, BossBar bar) {
        boolean automatic() {
            return playerId == null;
        }

        double progress() {
            return SmeltController.progress(this, System.nanoTime());
        }
    }
}
