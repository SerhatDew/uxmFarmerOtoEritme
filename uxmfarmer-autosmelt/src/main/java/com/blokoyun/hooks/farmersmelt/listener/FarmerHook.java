package com.blokoyun.hooks.farmersmelt.listener;
import com.blokoyun.hooks.farmersmelt.config.RuleConfiguration;
import com.blokoyun.hooks.farmersmelt.integration.FarmerDependency;
import com.blokoyun.hooks.farmersmelt.integration.FarmerReflection;
import com.blokoyun.hooks.farmersmelt.integration.FarmerStorage;
import com.blokoyun.hooks.farmersmelt.smelt.CommandContext;
import com.blokoyun.hooks.farmersmelt.smelt.ItemRule;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class FarmerHook {
    private final JavaPlugin plugin;
    private final Listener listener = new Listener() {
    };
    private final RuleConfiguration configuration;
    private Class<? extends Event> eventClass;
    private Plugin dependency;
    private boolean dependencyWarningLogged;
    private boolean threadWarningLogged;
    private boolean registered;
    private FarmerReflection.Binding binding;

    public FarmerHook(JavaPlugin plugin, RuleConfiguration configuration) {
        this.plugin = plugin;
        this.configuration = configuration;
    }



    public void unregister() {
        registered = false;
        eventClass = null;
        binding = null;
        dependency = null;
        org.bukkit.event.HandlerList.unregisterAll(listener);
    }

    public boolean isRegistered() {
        return registered;
    }

    public boolean register() {
        if (registered) return true;
        if (!configuration.enabled()) {
            plugin.getLogger().info("Hook config.yml içinde devre dışı bırakıldı.");
            return true;
        }
        Plugin dependency = FarmerDependency.find(Bukkit.getPluginManager(), configuration.pluginName());
        if (dependency == null || !dependency.isEnabled()) {
            if (!dependencyWarningLogged) {
                plugin.getLogger().warning("UXMFarmer bulunamadı veya etkin değil; hook daha sonra yeniden denenecek.");
                dependencyWarningLogged = true;
            }
            return false;
        }
        this.dependency = dependency;
        dependencyWarningLogged = false;

        Class<?> loadedClass = loadEventClass(configuration.eventClassName(), dependency);
        if (loadedClass == null || !Event.class.isAssignableFrom(loadedClass)) {
            plugin.getLogger().warning("Hook event sınıfı bulunamadı veya Bukkit Event değil: " + configuration.eventClassName());
            return false;
        }
        @SuppressWarnings("unchecked")
        Class<? extends Event> typedEventClass = (Class<? extends Event>) loadedClass;
        EventPriority priority = EventPriority.valueOf(configuration.priorityName().toUpperCase(Locale.ROOT));
        FarmerReflection.Binding newBinding;
        try {
            newBinding = FarmerReflection.bind(typedEventClass, plugin, dependency.getClass().getClassLoader());
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "UXMFarmer event API bağlanamadı", ex);
            return false;
        }
        EventExecutor executor = (listener, event) -> handleEvent(event);
        PluginManager manager = Bukkit.getPluginManager();
        try {
            manager.registerEvent(typedEventClass, listener, priority, executor, plugin);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("UXMFarmer hook kaydedilemedi: " + ex.getMessage());
            return false;
        }
        eventClass = typedEventClass;
        binding = newBinding;
        registered = true;
        plugin.getLogger().info("UXMFarmer hook aktif: " + typedEventClass.getName()
                + " (" + configuration.itemCount() + " özel item kuralı)");
        return true;
    }

    private Class<?> loadEventClass(String name, Plugin dependency) {
        ClassLoader[] classLoaders = {
                dependency.getClass().getClassLoader(),
                plugin.getClass().getClassLoader(),
                Thread.currentThread().getContextClassLoader()
        };
        for (ClassLoader classLoader : classLoaders) {
            if (classLoader == null) {
                continue;
            }
            try {
                return Class.forName(name, true, classLoader);
            } catch (ClassNotFoundException | LinkageError ignored) {
                // Bir sonraki plugin classloader denenir.
            }
        }
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }


    private void handleEvent(Event event) {
        if (!registered || eventClass == null || !eventClass.isInstance(event)) {
            return;
        }
        if (event instanceof org.bukkit.event.Cancellable cancellable && cancellable.isCancelled()) {
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            if (!threadWarningLogged) {
                plugin.getLogger().warning("UXMFarmer toplama olayı ana sunucu thread'i dışında geldi; komut hook'u atlandı.");
                threadWarningLogged = true;
            }
            return;
        }
        try {
            if (binding == null) return;
            FarmerReflection.EventData data = binding.readEvent(event);
            ItemRule rule = configuration.ruleFor(data.material());
            if (rule.mode() == ItemRule.RuleMode.PLUGIN || !rule.matches(data.amount())) {
                return;
            }
            FarmerReflection.OwnerData owner = binding.resolveOwner(data.farmerUuid());
            if (!rule.permission().isBlank() && (!owner.known() || !hasPermission(owner, rule.permission()))) {
                return;
            }
            if (rule.commands().isEmpty()) {
                plugin.getLogger().warning("commands modu seçilmiş ancak komut listesi boş: " + data.material());
                return;
            }
            if (!owner.known() && rule.commands().stream().anyMatch(this::needsOwner)) {
                plugin.getLogger().warning("Item komutu için UXMFarmer sahibi çözülemedi; ürün depoda bırakıldı: "
                        + data.material());
                return;
            }

            CommandContext context = CommandContext.of(data.material(), data.amount(), owner.name(),
                    owner.known() ? owner.uuid().toString() : "", data.farmerUuid().toString());
            int attemptedCommands = 0;
            for (String commandTemplate : rule.commands()) {
                String command = context.apply(commandTemplate).trim();
                if (command.startsWith("/")) {
                    command = command.substring(1).trim();
                }
                if (command.isBlank()) {
                    continue;
                }
                attemptedCommands++;
                boolean accepted = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                if (!accepted) {
                    plugin.getLogger().warning("Komut kabul edilmedi: " + command);
                }
            }
            // A command can have side effects before Bukkit reports false. Once any
            // command was attempted, leave the event consumed to prevent duplication.
            if (rule.consume() && attemptedCommands > 0) {
                // The collect event is raised after UXMFarmer has stored the item.
                // Mutating its event amount alone cannot undo that storage and caused
                // command rewards to duplicate products. Debit the same stock record.
                new FarmerStorage(dependency).consume(data.farmerUuid(), data.material(), data.amount());
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("UXMFarmer toplama hook'u çalıştırılamadı: " + ex.getMessage());
        }
    }

    private boolean hasPermission(FarmerReflection.OwnerData owner, String permission) {
        if (owner.uuid() == null) {
            return false;
        }
        org.bukkit.entity.Player player = Bukkit.getPlayer(owner.uuid());
        return player != null && player.hasPermission(permission);
    }

    private boolean needsOwner(String command) {
        return command.contains("%player%") || command.contains("%owner%") || command.contains("%uuid%")
                || command.contains("{player}") || command.contains("{owner}") || command.contains("{uuid}");
    }
}
