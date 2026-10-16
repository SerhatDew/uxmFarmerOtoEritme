package com.blokoyun.hooks.farmersmelt.listener;
import com.blokoyun.hooks.farmersmelt.PlayerMessages;
import com.blokoyun.hooks.farmersmelt.config.RuleConfiguration;
import com.blokoyun.hooks.farmersmelt.integration.FarmerDependency;
import com.blokoyun.hooks.farmersmelt.integration.FarmerStorage;
import com.blokoyun.hooks.farmersmelt.smelt.ItemRule;
import com.blokoyun.hooks.farmersmelt.smelt.SmeltController;
import com.blokoyun.hooks.farmersmelt.smelt.SmeltRecipe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.scheduler.BukkitTask;

/** Farmer menu bridge and the native-looking smelting inventory. */
public final class SmeltGuiHook implements Listener {
    private static final String[] OPEN_EVENTS = {
            "xyz.geik.farmer.api.handlers.FarmerMainGuiOpenEvent",
            "com.uxplima.uxmfarmer.api.event.impl.FarmerOpenMainMenuEvent"
    };
    private static final String GUI_PACKAGE = "com.uxplima.uxmfarmer.shaded.gui.";
    private static final String FARMER_GUI = "com.uxplima.uxmfarmer.gui.impl.farmer.FarmerGUI";
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final int OUTER_COLUMNS = 9;
    private static final int INNER_COLUMNS = 7;
    public static final String USE_PERMISSION = "uxmfarmerotosmelter.use";

    private final JavaPlugin plugin;
    private final RuleConfiguration configuration;
    private final SmeltController controller;
    private final Map<UUID, PendingMenu> pending = new HashMap<>();
    private final Map<UUID, InjectedButton> injected = new HashMap<>();
    private final Set<BukkitTask> scheduledTasks = new HashSet<>();
    private Class<? extends Event> menuEventClass;
    private MenuEventBinding menuBinding;
    private Plugin dependency;
    private boolean registered;
    private boolean warned;
    private boolean dependencyWarned;
    private boolean occupiedSlotWarned;
    private long generation;

    public SmeltGuiHook(JavaPlugin plugin, RuleConfiguration configuration, SmeltController controller) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.controller = controller;
    }

    public boolean register() {
        if (registered || !configuration.enabled() || !configuration.guiSettings().enabled()) return true;
        dependency = FarmerDependency.find(Bukkit.getPluginManager(), configuration.pluginName());
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
                } catch (ClassNotFoundException ignored) {
                    // Try the other supported Farmer generation.
                }
            }
            if (event == null) throw new ClassNotFoundException("Farmer GUI open event bulunamadı");
            menuEventClass = event.asSubclass(Event.class);
            bindMenuMethods();
            Bukkit.getPluginManager().registerEvent(menuEventClass, this, EventPriority.MONITOR,
                    (listener, incoming) -> captureFarmer(incoming), plugin, true);
            Bukkit.getPluginManager().registerEvents(this, plugin);
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

    private void bindMenuMethods() throws NoSuchMethodException {
        if (menuEventClass == null) throw new NoSuchMethodException("Farmer menü olayı yok");
        menuBinding = MenuEventBinding.bind(menuEventClass);
    }

    public boolean isRegistered() {
        return registered;
    }

    public boolean shutdown() {
        boolean clean = true;
        registered = false;
        generation++;
        for (BukkitTask task : scheduledTasks) {
            if (task == null) continue;
            try {
                task.cancel();
            } catch (RuntimeException | LinkageError ex) {
                clean = false;
                plugin.getLogger().log(Level.WARNING, "GUI görevi iptal edilemedi", ex);
            }
        }
        scheduledTasks.clear();
        try {
            HandlerList.unregisterAll(this);
        } catch (RuntimeException | LinkageError ex) {
            clean = false;
            plugin.getLogger().log(Level.WARNING, "GUI dinleyicisi kaldırılamadı", ex);
        }
        try {
            clean = closeOwnedMenus() && clean;
        } catch (RuntimeException | LinkageError ex) {
            clean = false;
            plugin.getLogger().log(Level.WARNING, "Açık GUI listesi alınamadı", ex);
        }
        pending.clear();
        injected.clear();
        menuBinding = null;
        menuEventClass = null;
        return clean;
    }

    private boolean closeOwnedMenus() {
        boolean clean = true;
        int closed = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                Inventory inventory = player.getOpenInventory().getTopInventory();
                InjectedButton button = injected.get(player.getUniqueId());
                boolean smeltMenu = inventory.getHolder() instanceof SmeltMenu menu && menu.owner == this;
                if (smeltMenu || button != null && button.inventory() == inventory) {
                    player.closeInventory();
                    closed++;
                }
            } catch (RuntimeException | LinkageError ex) {
                clean = false;
                plugin.getLogger().log(Level.WARNING, "GUI menüsü kapatılamadı", ex);
            }
        }
        if (closed > 0) plugin.getLogger().info("Hook'un açık menüleri kapatıldı: " + closed + ".");
        return clean;
    }

    private void captureFarmer(Event event) {
        if (menuEventClass == null || !menuEventClass.isInstance(event) || !Bukkit.isPrimaryThread()
                || event instanceof Cancellable cancellable && cancellable.isCancelled()) return;
        try {
            MenuEventBinding.MenuData data = menuBinding.read(event);
            if (data.legacyFarmer() != null) {
                controller.bindFarmer(data.farmerId(), data.legacyFarmer());
            }
            Player player = data.player();
            if (player != null) {
                PendingMenu request = new PendingMenu(data.farmerId());
                pending.put(player.getUniqueId(), request);
                scheduleLater(() -> pending.remove(player.getUniqueId(), request), 40L);
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Farmer menü bilgisi okunamadı", ex);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        PendingMenu request = pending.remove(player.getUniqueId());
        if (request == null) return;
        Inventory inventory = event.getInventory();
        int slot = configuration.guiSettings().slot();
        if (inventory.getType() != org.bukkit.event.inventory.InventoryType.CHEST
                || inventory.getSize() <= slot || !recognizedFarmerMenu(inventory.getHolder())) {
            plugin.getLogger().fine("Farmer menüsü düğme için uygun değil: boyut=" + inventory.getSize());
            return;
        }
        scheduleNow(() -> {
            if (registered && player.isOnline() && player.getOpenInventory().getTopInventory() == inventory) {
                injectButton(player, inventory, inventory.getHolder(), request.farmerId);
            }
        });
    }

    private boolean recognizedFarmerMenu(InventoryHolder holder) {
        if (holder == null) return false;
        try {
            holder.getClass().getMethod("getGuiItem", int.class);
            holder.getClass().getMethod("updateItem", int.class, Class.forName(
                    GUI_PACKAGE + "guis.GuiItem", true, holder.getClass().getClassLoader()));
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Older Farmer GUIs do not expose the shaded holder API; their own classloader is
            // still a reliable identity check, while unrelated chests are rejected.
            return dependency != null && holder.getClass().getClassLoader() == dependency.getClass().getClassLoader();
        }
    }

    private void injectButton(Player player, Inventory inventory, InventoryHolder holder, UUID farmerId) {
        int slot = configuration.guiSettings().slot();
        try {
            ClassLoader loader = holder.getClass().getClassLoader();
            Class<?> itemType = Class.forName(GUI_PACKAGE + "guis.GuiItem", true, loader);
            Class<?> actionType = Class.forName(GUI_PACKAGE + "components.GuiAction", true, loader);
            Object existing = holder.getClass().getMethod("getGuiItem", int.class).invoke(holder, slot);
            if (existing != null && itemType.getMethod("getAction").invoke(existing) != null) {
                warnOccupied(slot);
                return;
            }
            Object action = Proxy.newProxyInstance(loader, new Class<?>[]{actionType}, (proxy, method, args) -> {
                if (method.getName().equals("execute") && args != null && args.length == 1
                        && args[0] instanceof InventoryClickEvent click
                        && click.getWhoClicked().getUniqueId().equals(player.getUniqueId())
                        && click.getView().getTopInventory() == inventory && click.getRawSlot() == slot) {
                    click.setCancelled(true);
                    long actionGeneration = generation;
                    scheduleNow(() -> {
                        if (registered && actionGeneration == generation && player.isOnline()
                                && player.getOpenInventory().getTopInventory() == inventory) {
                            openMenu(player, farmerId, 0, inventory, inventory);
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
            // Never overwrite an active Farmer action when a GUI implementation changes.
            ItemStack current = inventory.getItem(slot);
            if (!safeToInject(current)) {
                warnOccupied(slot);
                return;
            }
            inventory.setItem(slot, icon(configuration.guiSettings().buttonMaterial(),
                    configuration.guiSettings().buttonName(), configuration.guiSettings().buttonLore()));
        }
        injected.put(player.getUniqueId(), new InjectedButton(inventory, farmerId));
    }

    private boolean safeToInject(ItemStack current) {
        return current == null || current.getType().isAir()
                || current.getType() == configuration.guiSettings().fillerMaterial();
    }

    private void warnOccupied(int slot) {
        if (!occupiedSlotWarned) {
            plugin.getLogger().warning("Oto eritme düğmesi eklenemedi: gui.slot=" + slot
                    + " Farmer menüsündeki başka bir düğme tarafından kullanılıyor. Boş bir slot seçin.");
            occupiedSlotWarned = true;
        }
    }

    private void openMenu(Player player, UUID farmerId, int page, Inventory originalMenu, Inventory expectedView) {
        if (!registered || !configuration.enabled() || !controller.authorized(player, farmerId, null)) return;
        int pageSize = pageSize();
        int pageCount = Math.max(1, (configuration.recipes().size() + pageSize - 1) / pageSize);
        int safePage = Math.max(0, Math.min(page, pageCount - 1));
        SmeltMenu menu = new SmeltMenu(this, player.getUniqueId(), farmerId, safePage,
                generation, originalMenu, configuration.guiSettings().rows());
        fillFrame(menu.inventory);
        menu.inventory.setItem(4, automaticInfo(farmerId));
        int start = safePage * pageSize;
        int index = 0;
        for (int row = 1; row < configuration.guiSettings().rows() - 1; row++) {
            for (int column = 1; column <= INNER_COLUMNS && start + index < configuration.recipes().size(); column++) {
                SmeltRecipe recipe = configuration.recipes().get(start + index++);
                int slot = row * OUTER_COLUMNS + column;
                menu.recipes.put(slot, recipe);
                menu.inventory.setItem(slot, recipeIcon(player, farmerId, recipe));
            }
        }
        int bottom = configuration.guiSettings().rows() * OUTER_COLUMNS - OUTER_COLUMNS;
        if (safePage > 0) menu.inventory.setItem(bottom + 1,
                icon(Material.ARROW, "<#ffea00>← Önceki Sayfa", List.of()));
        menu.inventory.setItem(bottom + 3, icon(Material.BARRIER, "<#ff0000>Geri Dön", List.of()));
        menu.inventory.setItem(bottom + 5, icon(Material.CHEST, "<#ffea00>Yenile", List.of()));
        menu.inventory.setItem(bottom + 4, icon(Material.PAPER,
                "<#ffea00>Sayfa " + (safePage + 1) + " / " + pageCount, List.of()));
        if (safePage + 1 < pageCount) menu.inventory.setItem(bottom + 7,
                icon(Material.ARROW, "<#ffea00>Sonraki Sayfa →", List.of()));
        scheduleNow(() -> {
            if (registered && generation == menu.generation && player.isOnline()
                    && player.getOpenInventory().getTopInventory() == expectedView) {
                player.openInventory(menu.inventory);
            }
        });
    }


    private int pageSize() {
        return INNER_COLUMNS * Math.max(1, configuration.guiSettings().rows() - 2);
    }

    private void fillFrame(Inventory inventory) {
        ItemStack filler = icon(configuration.guiSettings().fillerMaterial(), "<gray> ", List.of());
        int rows = inventory.getSize() / OUTER_COLUMNS;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < OUTER_COLUMNS; column++) {
                if (row == 0 || row == rows - 1 || column == 0 || column == OUTER_COLUMNS - 1) {
                    inventory.setItem(row * OUTER_COLUMNS + column, filler);
                }
            }
        }
    }

    private ItemStack recipeIcon(Player player, UUID farmerId, SmeltRecipe recipe) {
        int stock = 0;
        boolean sourceReadable = true;
        try {
            stock = controller.stock(farmerId, recipe.rule().material());
        } catch (ReflectiveOperationException | RuntimeException ex) {
            sourceReadable = false;
            plugin.getLogger().fine("Kaynak stoku okunamadı: " + ex.getMessage());
        }
        int amount = recipe.amountFor(stock);
        String output = recipe.output() == null ? "Tanımsız" : recipe.output().name();
        String currentOutput = "belirsiz";
        String capacity = "belirsiz";
        boolean canStart = sourceReadable && amount > 0;
        if (recipe.rule().mode() == ItemRule.RuleMode.COMMANDS) {
            currentOutput = "komut çıktısı";
            capacity = "uygulanmaz";
        } else if (recipe.output() == null) {
            currentOutput = "tanımsız ürün";
            capacity = "tanımsız ürün";
            canStart = false;
        } else {
            try {
                currentOutput = Integer.toString(controller.stock(farmerId, recipe.output()));
            } catch (ReflectiveOperationException | RuntimeException ex) {
                currentOutput = "kayıt yok";
            }
            // Snapshot is intentionally deferred until there is source to process. This avoids creating
            // a missing output record merely by opening the product menu.
            if (stock > 0) {
                try {
                    FarmerStorage.StockSnapshot snapshot = controller.snapshot(farmerId, recipe);
                    currentOutput = Integer.toString(snapshot.destinationAmount());
                    capacity = Integer.toString(snapshot.capacity());
                    canStart = amount > 0 && snapshot.destinationSpace() >= amount;
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    canStart = false;
                    capacity = "tanımsız ürün";
                }
            }
        }
        SmeltController.Operation operation = controller.operation(farmerId);
        boolean active = operation != null && operation.recipe().equals(recipe);
        boolean farmerBusy = operation != null;
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Mevcut stok: <#05e300>" + (sourceReadable ? Integer.toString(stock) : "okunamadı"));
        lore.add("<gray>Çıktı: <#5b9397>" + output);
        lore.add("<gray>Çıktı stoku: <#05e300>" + currentOutput + " <gray>/ <#e3000e>" + capacity);
        lore.add("");
        lore.add("<#5b9397>Üretim ↓");
        lore.add("<gray>Parti miktarı: <#ffea00>" + amount);
        lore.add("<gray>Süre (manuel): <#ffea00>" + (controller.instant(player) ? "Beklemesiz" : recipe.durationSeconds() + " s"));
        lore.add("<gray>Otomatik: " + automaticStatus(farmerId, recipe));
        if (active) lore.add("<gray>İşlem: <#ffea00>" + (int) (operation.progress() * 100) + "%");
        lore.add("");
        lore.add(active ? "<yellow>İşlem devam ediyor"
                : farmerBusy ? "<yellow>Bu Farmer'da başka bir işlem var"
                : canStart ? "<yellow>Başlatmak için tıkla" : "<red>Stok veya kapasite uygun değil");
        Material material = recipe.rule().material() == null ? Material.BARRIER : recipe.rule().material();
        return icon(material, Component.translatable(material.translationKey()).color(NamedTextColor.GRAY), lore);
    }

    private ItemStack automaticInfo(UUID farmerId) {
        boolean allowed = controller.automaticAllowed(farmerId, null);
        boolean enabled = configuration.autoSmeltEnabled() && controller.automaticAvailable() && allowed;
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Durum: " + (!configuration.autoSmeltEnabled() ? "<red>Kapalı"
                : !controller.automaticAvailable() ? "<red>Kullanılamıyor"
                : allowed ? "<#05e300>Açık" : "<red>Sahibi yetkisiz veya çevrimdışı"));
        lore.add("<gray>Kaynak ve çıktı aynı Farmer deposundadır.");
        lore.add("<gray>Gerekli izin: <#ffea00>" + (configuration.autoSmeltPermission().isBlank()
                ? "Herkese açık" : configuration.autoSmeltPermission()));
        lore.add("<gray>Otomatik süre: <#ffea00>" + (controller.instantOwner(farmerId) ? "Beklemesiz" : "Tarife göre"));
        SmeltController.Operation operation = controller.operation(farmerId);
        if (operation != null) {
            lore.add("");
            lore.add("<#5b9397>Devam Eden İşlem ↓");
            lore.add("<gray>Ürün: <#ffea00>" + operation.recipe().rule().material().name());
            lore.add("<gray>Miktar: <#ffea00>" + operation.amount());
            lore.add("<gray>İlerleme: <#ffea00>" + (int) (operation.progress() * 100) + "%");
        }
        return icon(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                "<#ffea00>Otomatik Eritme", lore);
    }

    private String automaticStatus(UUID farmerId, SmeltRecipe recipe) {
        if (!configuration.autoSmeltEnabled()) return "<red>kapalı";
        if (!controller.automaticAvailable()) return "<red>kullanılamıyor";
        if (!SmeltController.isAutomaticRecipe(recipe)) {
            return "<gray>uygulanmaz";
        }
        if (!controller.automaticAllowed(farmerId, recipe.rule())) return "<red>yetkisiz / çevrimdışı";
        return controller.instantOwner(farmerId) ? "<#05e300>açık · beklemesiz" : "<#05e300>açık";
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        InjectedButton button = injected.get(player.getUniqueId());
        if (button != null && event.getView().getTopInventory() == button.inventory
                && event.getRawSlot() == configuration.guiSettings().slot()) {
            event.setCancelled(true);
            scheduleNow(() -> {
                if (registered && player.isOnline() && player.getOpenInventory().getTopInventory() == button.inventory) {
                    openMenu(player, button.farmerId, 0, button.inventory, button.inventory);
                }
            });
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof SmeltMenu menu) || menu.owner != this
                || !registered || !menu.playerId.equals(player.getUniqueId()) || menu.generation != generation) return;
        // Cancel bottom-inventory shift/number-key clicks as well as top clicks.
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= menu.inventory.getSize()) return;
        SmeltRecipe recipe = menu.recipes.get(event.getRawSlot());
        if (recipe != null) {
            controller.start(player, menu.farmerId, recipe);
            return;
        }
        int bottom = menu.inventory.getSize() - OUTER_COLUMNS;
        int rawSlot = event.getRawSlot();
        scheduleNow(() -> {
            if (!isCurrent(player, menu)) return;
            if (rawSlot == bottom + 5) {
                openMenu(player, menu.farmerId, menu.page, menu.originalMenu, menu.inventory);
            } else if (rawSlot == bottom + 3) {
                player.closeInventory();
                openFarmerMenu(player, menu.farmerId, menu.originalMenu);
            } else if (rawSlot == bottom + 1 && menu.page > 0) {
                openMenu(player, menu.farmerId, menu.page - 1, menu.originalMenu, menu.inventory);
            } else if (rawSlot == bottom + 7
                    && (menu.page + 1) * pageSize() < configuration.recipes().size()) {
                openMenu(player, menu.farmerId, menu.page + 1, menu.originalMenu, menu.inventory);
            }
        });
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SmeltMenu menu && menu.owner == this
                && registered && menu.generation == generation) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        InjectedButton button = injected.get(playerId);
        if (button != null && button.inventory == event.getInventory()) {
            injected.remove(playerId);
        }
        if (event.getInventory().getHolder() instanceof SmeltMenu menu && menu.owner == this) {
            injected.remove(playerId);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        pending.remove(playerId);
        injected.remove(playerId);
        controller.cancelPlayer(playerId);
    }

    private boolean isCurrent(Player player, SmeltMenu menu) {
        return registered && generation == menu.generation && player.isOnline()
                && menu.playerId.equals(player.getUniqueId())
                && player.getOpenInventory().getTopInventory() == menu.inventory;
    }

    private void openFarmerMenu(Player player, UUID farmerId, Inventory originalMenu) {
        if (!registered || !player.isOnline() || !controller.authorized(player, farmerId, null)) return;
        try {
            Class<?> farmerGui = Class.forName(FARMER_GUI, true, dependency.getClass().getClassLoader());
            Object gui = farmerGui.getConstructor(UUID.class).newInstance(farmerId);
            farmerGui.getMethod("open", Player.class).invoke(gui, player);
            return;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            plugin.getLogger().log(Level.WARNING, "Farmer ana menüsüne geri dönülemedi; eski menü deneniyor", ex);
        }
        if (originalMenu != null) {
            try {
                player.openInventory(originalMenu);
                return;
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Farmer eski menüsü yeniden açılamadı", ex);
            }
        }
        PlayerMessages.send(player, "§cᴄɪꜰᴛᴄɪ ᴍᴇɴᴜsᴜɴᴇ ɢᴇʀɪ ᴅᴏɴᴜʟᴇᴍᴇᴅɪ.");
    }

    private BukkitTask scheduleNow(Runnable action) {
        return schedule(action, false, 0L);
    }

    private BukkitTask scheduleLater(Runnable action, long delayTicks) {
        return schedule(action, true, delayTicks);
    }

    private BukkitTask schedule(Runnable action, boolean delayed, long delayTicks) {
        final BukkitTask[] holder = new BukkitTask[1];
        Runnable owned = () -> {
            BukkitTask task = holder[0];
            if (task != null) scheduledTasks.remove(task);
            action.run();
        };
        BukkitTask task = delayed
                ? Bukkit.getScheduler().runTaskLater(plugin, owned, delayTicks)
                : Bukkit.getScheduler().runTask(plugin, owned);
        holder[0] = task;
        if (task != null) scheduledTasks.add(task);
        return task;
    }

    private static ItemStack icon(Material material, String name, List<String> lore) {
        return icon(material, parseText(name), lore);
    }

    private static ItemStack icon(Material material, Component name, List<String> lore) {
        ItemStack item = ItemStack.of(material == null || material.isAir() ? Material.BARRIER : material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        List<Component> components = new ArrayList<>(lore.size());
        for (String line : lore) components.add(parseText(line).decoration(TextDecoration.ITALIC, false));
        meta.lore(components);
        item.setItemMeta(meta);
        return item;
    }

    private static Component parseText(String text) {
        String value = text == null ? "" : text;
        if (value.indexOf('&') >= 0) value = ChatColor.translateAlternateColorCodes('&', value);
        return value.indexOf(ChatColor.COLOR_CHAR) >= 0
                ? LegacyComponentSerializer.legacySection().deserialize(value) : MINI.deserialize(value);
    }

    private static final class MenuEventBinding {
        private final Method playerMethod;
        private final Method farmerUuidMethod;
        private final Method farmerMethod;
        private final Method regionMethod;

        private MenuEventBinding(Method playerMethod, Method farmerUuidMethod,
                                 Method farmerMethod, Method regionMethod) {
            this.playerMethod = playerMethod;
            this.farmerUuidMethod = farmerUuidMethod;
            this.farmerMethod = farmerMethod;
            this.regionMethod = regionMethod;
        }

        static MenuEventBinding bind(Class<? extends Event> eventClass) throws NoSuchMethodException {
            Method player = eventClass.getMethod("getPlayer");
            try {
                return new MenuEventBinding(player, eventClass.getMethod("getFarmerUUID"), null, null);
            } catch (NoSuchMethodException modernMissing) {
                Method farmer = eventClass.getMethod("getFarmer");
                Method region = farmer.getReturnType().getMethod("getRegionID");
                return new MenuEventBinding(player, null, farmer, region);
            }
        }

        MenuData read(Event event) throws ReflectiveOperationException {
            Object playerValue = playerMethod.invoke(event);
            Player player = playerValue instanceof Player value ? value : null;
            if (farmerUuidMethod != null) {
                Object value = farmerUuidMethod.invoke(event);
                if (!(value instanceof UUID uuid)) throw new IllegalStateException("Farmer UUID türü geçersiz");
                return new MenuData(uuid, null, player);
            }
            Object farmer = farmerMethod.invoke(event);
            Object region = regionMethod.invoke(farmer);
            UUID farmerId = UUID.nameUUIDFromBytes(("farmer:" + region).getBytes(StandardCharsets.UTF_8));
            return new MenuData(farmerId, farmer, player);
        }

        private record MenuData(UUID farmerId, Object legacyFarmer, Player player) { }
    }

    private record PendingMenu(UUID farmerId) { }

    private record InjectedButton(Inventory inventory, UUID farmerId) { }

    private static final class SmeltMenu implements InventoryHolder {
        private final SmeltGuiHook owner;
        private final UUID playerId;
        private final UUID farmerId;
        private final int page;
        private final long generation;
        private final Inventory originalMenu;
        private final Map<Integer, SmeltRecipe> recipes = new HashMap<>();
        private final Inventory inventory;

        private SmeltMenu(SmeltGuiHook owner, UUID playerId, UUID farmerId, int page, long generation,
                          Inventory originalMenu, int rows) {
            this.owner = owner;
            this.playerId = playerId;
            this.farmerId = farmerId;
            this.page = page;
            this.generation = generation;
            this.originalMenu = originalMenu;
            this.inventory = Bukkit.createInventory(this, rows * OUTER_COLUMNS,
                    parseText(owner.configuration.guiSettings().title()));
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
