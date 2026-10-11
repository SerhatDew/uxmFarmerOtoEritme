package com.blokoyun.uxmfarmerautosmelt;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

final class SmeltGuiHook implements Listener {
    private static final String[] OPEN_EVENTS = {
            "xyz.geik.farmer.api.handlers.FarmerMainGuiOpenEvent",
            "com.uxplima.uxmfarmer.api.event.impl.FarmerOpenMainMenuEvent"
    };
    private static final String GUI_PACKAGE = "com.uxplima.uxmfarmer.shaded.gui.";
    private static final int PAGE_SIZE = 45;
    static final String USE_PERMISSION = "uxmfarmerotosmelter.use";

    private final JavaPlugin plugin;
    private final RuleConfiguration configuration;
    private final Map<UUID, PendingMenu> pending = new HashMap<>();
    private final Map<UUID, InjectedButton> injected = new HashMap<>();
    private final Map<UUID, Operation> operations = new HashMap<>();
    private Class<? extends Event> menuEventClass;
    private FarmerStorage storage;
    private BukkitTask ticker;
    private boolean registered;
    private boolean warned;
    private boolean dependencyWarned;
    private boolean occupiedSlotWarned;
    private long generation;

    SmeltGuiHook(JavaPlugin plugin, RuleConfiguration configuration) {
        this.plugin = plugin;
        this.configuration = configuration;
    }

    boolean register() {
        if (registered || !configuration.enabled() || !configuration.guiSettings().enabled()) return true;
        Plugin dependency = FarmerDependency.find(Bukkit.getPluginManager(), configuration.pluginName());
        if (dependency == null || !dependency.isEnabled()) {
            if (!dependencyWarned) {
                plugin.getLogger().warning("Farmer GUI bağlanamadı: Farmer eklentisi bulunamadı veya etkin değil. "
                        + "Farmer etkinleşince bağlantı yeniden denenecek.");
                dependencyWarned = true;
            }
            return false;
        }
        dependencyWarned = false;
        try {
            Class<?> event = null;
            for (String eventName : OPEN_EVENTS) {
                try {
                    event = Class.forName(eventName, true, dependency.getClass().getClassLoader());
                    break;
                } catch (ClassNotFoundException ignored) { }
            }
            if (event == null) throw new ClassNotFoundException("Farmer GUI open event bulunamadı");
            menuEventClass = event.asSubclass(Event.class);
            storage = new FarmerStorage(dependency);
            Bukkit.getPluginManager().registerEvent(menuEventClass, this, EventPriority.MONITOR,
                    (listener, incoming) -> captureFarmer(incoming), plugin, true);
            Bukkit.getPluginManager().registerEvents(this, plugin);
            ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tickOperations, 5L, 5L);
            registered = true;
            plugin.getLogger().info("Farmer GUI oto eritme düğmesi aktif: " + dependency.getName()
                    + "; slot: " + configuration.guiSettings().slot() + "; yetki: " + USE_PERMISSION);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            HandlerList.unregisterAll(this);
            if (!warned) {
                plugin.getLogger().log(Level.WARNING, "Farmer GUI hook'u kurulamadı", ex);
                warned = true;
            }
            return false;
        }
    }

    boolean isRegistered() {
        return registered;
    }

    void shutdown() {
        generation++;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof SmeltMenu menu
                    && menu.owner == this) {
                player.closeInventory();
            }
        }
        HandlerList.unregisterAll(this);
        if (ticker != null) ticker.cancel();
        operations.values().forEach(operation -> operation.bar.removeAll());
        operations.clear();
        pending.clear();
        injected.clear();
        registered = false;
    }

    private void captureFarmer(Event event) {
        if (!menuEventClass.isInstance(event) || !Bukkit.isPrimaryThread()
                || event instanceof Cancellable cancellable && cancellable.isCancelled()) return;
        try {
            UUID farmer;
            try {
                farmer = (UUID) event.getClass().getMethod("getFarmerUUID").invoke(event);
            } catch (NoSuchMethodException legacyMissing) {
                Object farmerObject = event.getClass().getMethod("getFarmer").invoke(event);
                farmer = UUID.nameUUIDFromBytes(("farmer:" + farmerObject.getClass().getMethod("getRegionID").invoke(farmerObject)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                storage.bind(farmer, farmerObject);
            }
            Object value = event.getClass().getMethod("getPlayer").invoke(event);
            if (value instanceof Player player) {
                PendingMenu request = new PendingMenu(farmer);
                pending.put(player.getUniqueId(), request);
                Bukkit.getScheduler().runTaskLater(plugin, () -> pending.remove(player.getUniqueId(), request), 40L);
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Farmer menü bilgisi okunamadı", ex);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inventory = event.getInventory();
        PendingMenu request = pending.get(player.getUniqueId());
        if (request == null) return;
        if (inventory.getSize() <= configuration.guiSettings().slot()
                || inventory.getType() != org.bukkit.event.inventory.InventoryType.CHEST) {
            plugin.getLogger().fine("Farmer menüsü düğme için uygun değil: boyut=" + inventory.getSize());
            return;
        }
        pending.remove(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (registered && player.isOnline() && player.getOpenInventory().getTopInventory() == inventory) {
                injectButton(player, inventory, inventory.getHolder(), request.farmerId);
            }
        });
    }

    private void injectButton(Player player, Inventory inventory, InventoryHolder holder, UUID farmerId) {
        try {
            int slot = configuration.guiSettings().slot();
            ClassLoader loader = holder.getClass().getClassLoader();
            Class<?> itemType = Class.forName(GUI_PACKAGE + "guis.GuiItem", true, loader);
            Class<?> actionType = Class.forName(GUI_PACKAGE + "components.GuiAction", true, loader);
            Object existing = holder.getClass().getMethod("getGuiItem", int.class).invoke(holder, slot);
            if (existing != null && itemType.getMethod("getAction").invoke(existing) != null) {
                if (!occupiedSlotWarned) {
                    plugin.getLogger().warning("Oto eritme düğmesi eklenemedi: gui.slot=" + slot
                            + " Farmer menüsündeki başka bir düğme tarafından kullanılıyor. Boş bir slot seçin.");
                    occupiedSlotWarned = true;
                }
                return;
            }
            Object action = Proxy.newProxyInstance(loader, new Class<?>[] {actionType}, (proxy, method, args) -> {
                if (method.getName().equals("execute") && args != null && args.length == 1
                        && args[0] instanceof InventoryClickEvent click
                        && click.getWhoClicked().getUniqueId().equals(player.getUniqueId())
                        && click.getView().getTopInventory() == inventory && click.getRawSlot() == slot) {
                    click.setCancelled(true);
                    long actionGeneration = generation;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (registered && actionGeneration == generation && player.isOnline()
                                && player.getOpenInventory().getTopInventory() == inventory) {
                            openMenu(player, farmerId, 0);
                        }
                    });
                }
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return args != null && proxy == args[0];
                if (method.getName().equals("toString")) return "BlokOyunSmeltAction";
                return null;
            });
            Object guiItem = itemType.getConstructor(ItemStack.class, actionType).newInstance(
                    icon(configuration.guiSettings().buttonMaterial(), configuration.guiSettings().buttonName(),
                            configuration.guiSettings().buttonLore()), action);
            holder.getClass().getMethod("updateItem", int.class, itemType).invoke(holder, slot, guiItem);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            inventory.setItem(configuration.guiSettings().slot(), icon(configuration.guiSettings().buttonMaterial(),
                    configuration.guiSettings().buttonName(), configuration.guiSettings().buttonLore()));
        }
        injected.put(player.getUniqueId(), new InjectedButton(inventory, farmerId));
    }

    private boolean authorized(Player player, UUID farmerId, ItemRule rule) {
        String extra = configuration.guiSettings().permission();
        if (!player.hasPermission(USE_PERMISSION) || !extra.isBlank() && !player.hasPermission(extra)
                || rule != null && !rule.permission().isBlank() && !player.hasPermission(rule.permission())) {
            PlayerMessages.send(player, "§cᴏᴛᴏ ᴇʀɪᴛᴍᴇ ʏᴇᴛᴋɪɴ ʏᴏᴋ.");
            return false;
        }
        try {
            if (!storage.canAccess(farmerId, player)) {
                PlayerMessages.send(player, "§cʏᴀʟɴɪᴢᴄᴀ ᴋᴇɴᴅɪ ᴄɪꜰᴛᴄɪɴᴅᴇ ᴇʀɪᴛᴍᴇ ʏᴀᴘᴀʙɪʟɪʀsɪɴ.");
                return false;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            PlayerMessages.send(player, "§cᴄɪꜰᴛᴄɪ ᴅᴇᴘᴏsᴜɴᴀ ᴜʟᴀsɪʟᴀᴍᴀᴅɪ.");
            plugin.getLogger().log(Level.WARNING, "Farmer erişimi doğrulanamadı", ex);
            return false;
        }
    }

    private void openMenu(Player player, UUID farmerId, int page) {
        if (!authorized(player, farmerId, null)) return;
        if (!registered || !configuration.enabled()) return;
        SmeltMenu menu = new SmeltMenu(this, player.getUniqueId(), farmerId, page);
        List<SmeltRecipe> recipes = configuration.recipes();
        int start = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE && start + slot < recipes.size(); slot++) {
            SmeltRecipe recipe = recipes.get(start + slot);
            menu.recipes.put(slot, recipe);
            int stock = 0;
            try { stock = storage.amount(farmerId, recipe.rule().material()); }
            catch (ReflectiveOperationException | RuntimeException ex) { plugin.getLogger().fine(ex.getMessage()); }
            int amount = recipe.amountFor(stock);
            menu.inventory.setItem(slot, icon(recipe.rule().material(), "&e" + recipe.rule().material().name(),
                    List.of("&7sᴛᴏᴋ: &f" + stock, "&7ᴍɪᴋᴛᴀʀ: &f" + amount,
                            "&7sᴜʀᴇ: &f" + recipe.durationSeconds() + " s", "", "&eᴇʀɪᴛᴍᴇᴋ ɪᴄɪɴ ᴛɪᴋʟᴀ")));
        }
        if (page > 0) menu.inventory.setItem(45, icon(Material.ARROW, "&eᴏɴᴄᴇᴋɪ sᴀʏꜰᴀ", List.of()));
        if (start + PAGE_SIZE < recipes.size()) menu.inventory.setItem(53, icon(Material.ARROW, "&esᴏɴʀᴀᴋɪ sᴀʏꜰᴀ", List.of()));
        menu.inventory.setItem(48, icon(Material.BARRIER, "&cᴋᴀᴘᴀᴛ", List.of()));
        menu.inventory.setItem(50, icon(Material.CHEST, "&eʏᴇɴɪʟᴇ", List.of()));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (registered && player.isOnline()) player.openInventory(menu.inventory);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && event.getRawSlot() == configuration.guiSettings().slot()) {
            InjectedButton button = injected.get(player.getUniqueId());
            if (button != null && event.getView().getTopInventory() == button.inventory) {
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (registered && player.isOnline() && player.getOpenInventory().getTopInventory() == button.inventory) {
                        openMenu(player, button.farmerId, 0);
                    }
                });
                return;
            }
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof SmeltMenu menu) || menu.owner != this
                || !registered) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !menu.playerId.equals(player.getUniqueId())
                || event.getRawSlot() < 0 || event.getRawSlot() >= menu.inventory.getSize()) return;
        SmeltRecipe recipe = menu.recipes.get(event.getRawSlot());
        if (recipe != null) { start(player, menu.farmerId, recipe); return; }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!registered || !player.isOnline() || player.getOpenInventory().getTopInventory() != menu.inventory) return;
            if (event.getRawSlot() == 50) { player.closeInventory(); openMenu(player, menu.farmerId, menu.page); }
            else if (event.getRawSlot() == 48) player.closeInventory();
            else if (event.getRawSlot() == 45 && menu.page > 0) { player.closeInventory(); openMenu(player, menu.farmerId, menu.page - 1); }
            else if (event.getRawSlot() == 53 && (menu.page + 1) * PAGE_SIZE < configuration.recipes().size()) { player.closeInventory(); openMenu(player, menu.farmerId, menu.page + 1); }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SmeltMenu menu && menu.owner == this && registered) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
        injected.remove(event.getPlayer().getUniqueId());
        operations.values().stream().filter(operation -> operation.playerId.equals(event.getPlayer().getUniqueId()))
                .findFirst().ifPresent(operation -> cancel(operation, null));
    }

    private void start(Player player, UUID farmerId, SmeltRecipe recipe) {
        if (!authorized(player, farmerId, recipe.rule())) return;
        if (operations.containsKey(farmerId) || operations.values().stream().anyMatch(o -> o.playerId.equals(player.getUniqueId()))) {
            PlayerMessages.send(player, "§eʙɪʀ ᴇʀɪᴛᴍᴇ ɪsʟᴇᴍɪ ᴢᴀᴛᴇɴ ᴅᴇᴠᴀᴍ ᴇᴅɪʏᴏʀ.");
            return;
        }
        try {
            FarmerStorage.StockSnapshot snapshot = storage.snapshot(farmerId, recipe);
            int amount = recipe.amountFor(snapshot.sourceAmount());
            if (amount == 0 || recipe.rule().mode() == ItemRule.RuleMode.PLUGIN && snapshot.destinationSpace() < amount) {
                PlayerMessages.send(player, "§cᴅᴇᴘᴏ ᴠᴇʏᴀ ᴍɪᴋᴛᴀʀ sᴀʀᴛɪ ᴜʏɢᴜɴ ᴅᴇɢɪʟ.");
                return;
            }
            BossBar bar = Bukkit.createBossBar("ᴇʀɪᴛɪʟɪʏᴏʀ", BarColor.YELLOW, BarStyle.SOLID);
            bar.setProgress(0.0);
            bar.addPlayer(player);
            operations.put(farmerId, new Operation(player.getUniqueId(), farmerId, recipe, amount, System.nanoTime(), bar));
            PlayerMessages.send(player, "§eᴇʀɪᴛᴍᴇ ʙᴀsʟᴀᴅɪ: §f" + recipe.durationSeconds() + " s");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            PlayerMessages.send(player, "§cᴇʀɪᴛᴍᴇ ʙᴀsʟᴀᴛɪʟᴀᴍᴀᴅɪ.");
            plugin.getLogger().log(Level.WARNING, "Farmer eritme başlatılamadı", ex);
        }
    }

    private void tickOperations() {
        for (Operation operation : new ArrayList<>(operations.values())) {
            Player player = Bukkit.getPlayer(operation.playerId);
            if (player == null || !player.isOnline()) { cancel(operation, null); continue; }
            double progress = Math.min(1, (System.nanoTime() - operation.startedAt)
                    / (recipeNanos(operation.recipe) * 1.0));
            operation.bar.setProgress(progress);
            operation.bar.setTitle(color(configuration.guiSettings().progressName()) + " §f" + (int) (progress * 100) + "%");
            if (progress >= 1) {
                operations.remove(operation.farmerId);
                operation.bar.removeAll();
                complete(operation, player);
            }
        }
    }

    private static long recipeNanos(SmeltRecipe recipe) { return recipe.durationSeconds() * 1_000_000_000L; }

    private void complete(Operation operation, Player player) {
        if (!authorized(player, operation.farmerId, operation.recipe.rule())) return;
        try {
            FarmerStorage.StockSnapshot snapshot = storage.snapshot(operation.farmerId, operation.recipe);
            CommandContext context = CommandContext.of(operation.recipe.rule().material(), operation.amount,
                    player.getName(), player.getUniqueId().toString(), operation.farmerId.toString());
            List<String> commands = operation.recipe.rule().commands().stream().map(context::apply).toList();
            SmeltTransaction.Result result = SmeltTransaction.complete(snapshot, operation.recipe.rule().mode(), operation.amount,
                    commands, command -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
            if (result == SmeltTransaction.Result.COMPLETED) {
                PlayerMessages.send(player, "§aᴇʀɪᴛᴍᴇ ᴛᴀᴍᴀᴍʟᴀɴᴅɪ.");
            } else if (result == SmeltTransaction.Result.COMMAND_FAILED) {
                PlayerMessages.send(player, "§cᴋᴏᴍᴜᴛ ᴜʏɢᴜʟᴀɴᴀᴍᴀᴅɪ; ᴛᴇᴋʀᴀʀ ᴜʀᴜɴ ᴠᴇʀɪʟᴍᴇᴅɪ.");
                plugin.getLogger().warning("Manual eritme komutu başarısız oldu; kaynak geri alınmadı: farmer=" + operation.farmerId);
            } else {
                PlayerMessages.send(player, "§cᴇʀɪᴛᴍᴇ ᴛᴀᴍᴀᴍʟᴀɴᴀᴍᴀᴅɪ; ᴋᴀʏɴᴀᴋ ᴅᴇᴘᴏᴅᴀ ᴋᴀʟᴅɪ.");
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            PlayerMessages.send(player, "§cᴇʀɪᴛᴍᴇ ɪsʟᴇᴍɪ ʙᴀsᴀʀɪsɪᴢ.");
            plugin.getLogger().log(Level.SEVERE, "Farmer eritme tamamlanamadı", ex);
        }
    }

    private void cancel(Operation operation, Player player) {
        operations.remove(operation.farmerId);
        operation.bar.removeAll();
        if (player != null) PlayerMessages.send(player, "§eɪsʟᴇᴍ ɪᴘᴛᴀʟ. ᴜʀᴜɴʟᴇʀ ᴅᴇᴘᴏᴅᴀ ᴋᴀʟᴅɪ.");
    }

    private static ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        meta.setLore(lore.stream().map(SmeltGuiHook::color).toList());
        item.setItemMeta(meta);
        return item;
    }

    private static String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }

    private record PendingMenu(UUID farmerId) { }
    private record InjectedButton(Inventory inventory, UUID farmerId) { }
    private record Operation(UUID playerId, UUID farmerId, SmeltRecipe recipe, int amount, long startedAt, BossBar bar) { }

    private static final class SmeltMenu implements InventoryHolder {
        private final SmeltGuiHook owner;
        private final UUID playerId;
        private final UUID farmerId;
        private final int page;
        private final Map<Integer, SmeltRecipe> recipes = new HashMap<>();
        private final Inventory inventory;
        private SmeltMenu(SmeltGuiHook owner, UUID playerId, UUID farmerId, int page) {
            this.owner = owner;
            this.playerId = playerId;
            this.farmerId = farmerId;
            this.page = page;
            this.inventory = Bukkit.createInventory(this, 54, "ᴏᴛᴏ ᴇʀɪᴛᴍᴇ");
        }
        @Override public Inventory getInventory() { return inventory; }
    }
}
