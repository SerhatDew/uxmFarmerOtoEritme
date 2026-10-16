package com.blokoyun.hooks.farmersmelt.integration;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;

/** Resolves the supported event/API generation once when the integration starts. */
public final class FarmerReflection {
    private static final String API_CLASS = "com.uxplima.uxmfarmer.api.UXMFarmerAPI";
    private static final String FARMER_CLASS = "com.uxplima.uxmfarmer.entity.Farmer";

    private FarmerReflection() { }

    public static Binding bind(Class<? extends Event> eventClass, JavaPlugin plugin,
                               ClassLoader dependencyClassLoader) throws ReflectiveOperationException {
        return new Binding(EventMethods.bind(eventClass), bindOwner(plugin, dependencyClassLoader), plugin);
    }

    public static final class Binding {
        private final EventMethods event;
        private final OwnerMethods owner;
        private final JavaPlugin plugin;

        private Binding(EventMethods event, OwnerMethods owner, JavaPlugin plugin) {
            this.event = event;
            this.owner = owner;
            this.plugin = plugin;
        }

        public EventData readEvent(Event incoming) throws ReflectiveOperationException {
            boolean legacy = event.farmer() != null;
            Object farmer = legacy ? event.farmer().invoke(incoming) : incoming;
            Object item = legacy ? event.item().invoke(incoming) : incoming;
            Object material = event.material().invoke(item);
            Object amount = event.amount().invoke(incoming);
            Object farmerUuid = event.farmerId().invoke(farmer);
            if (!(material instanceof Material typedMaterial) || !(amount instanceof Number count)
                    || !(farmerUuid instanceof UUID uuid)) {
                throw new IllegalStateException("Farmer event getter dönüş tipleri beklenen türde değil");
            }
            return new EventData(typedMaterial, count.intValue(), uuid,
                    legacy ? null : event.mutate(), legacy ? event.mutate() : null);
        }

        public OwnerData resolveOwner(UUID farmerUuid) {
            if (owner == null) return legacyOwner(farmerUuid);
            try {
                Object result = owner.byUuid().invoke(null, farmerUuid);
                if (!(result instanceof Optional<?> optional) || optional.isEmpty()) return OwnerData.unknown();
                Object value = owner.ownerUuid().invoke(optional.get());
                return value instanceof UUID uuid ? namedOwner(uuid) : OwnerData.unknown();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                plugin.getLogger().fine("UXMFarmer sahibine reflection ile ulaşılamadı: " + ex.getMessage());
                return legacyOwner(farmerUuid);
            }
        }

        private OwnerData legacyOwner(UUID farmerUuid) {
            return namedOwner(farmerUuid);
        }
    }

    private record EventMethods(Method material, Method amount, Method farmerId, Method mutate,
                                Method farmer, Method item) {
        static EventMethods bind(Class<? extends Event> type) throws NoSuchMethodException {
            try {
                return new EventMethods(type.getMethod("getCollectedProduct"), type.getMethod("getCollectedAmount"),
                        type.getMethod("getFarmerUUID"), type.getMethod("setCollectedAmount", int.class), null, null);
            } catch (NoSuchMethodException modernMissing) {
                Method farmer = type.getMethod("getFarmer");
                Method item = type.getMethod("getItem");
                Method id;
                try {
                    id = farmer.getReturnType().getMethod("getId");
                } catch (NoSuchMethodException legacyOwnerId) {
                    id = farmer.getReturnType().getMethod("getOwnerUUID");
                }
                return new EventMethods(item.getReturnType().getMethod("getType"), type.getMethod("getCollectAmount"),
                        id, type.getMethod("setCancelled", boolean.class), farmer, item);
            }
        }
    }

    private record OwnerMethods(Method byUuid, Method ownerUuid) { }

    private static OwnerMethods bindOwner(JavaPlugin plugin, ClassLoader dependencyClassLoader) {
        try {
            Class<?> api = loadApiClass(plugin, dependencyClassLoader);
            Class<?> farmer = Class.forName(FARMER_CLASS, false, api.getClassLoader());
            return new OwnerMethods(api.getMethod("getByUUID", UUID.class), farmer.getMethod("getOwnerUUID"));
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().fine("Modern sahip API'si yok; legacy Farmer sahip kimliği kullanılacak: " + ex.getMessage());
            return null;
        }
    }

    private static OwnerData namedOwner(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        String name = player.getName();
        return new OwnerData(uuid, name == null || name.isBlank() ? uuid.toString() : name);
    }

    public static Class<?> loadApiClass(JavaPlugin plugin, ClassLoader dependencyClassLoader)
            throws ClassNotFoundException {
        ClassLoader[] classLoaders = {dependencyClassLoader, plugin.getClass().getClassLoader(),
                Thread.currentThread().getContextClassLoader()};
        for (ClassLoader loader : classLoaders) {
            if (loader == null) continue;
            try {
                return Class.forName(API_CLASS, false, loader);
            } catch (ClassNotFoundException ignored) {
                // Try the next existing plugin classloader; no custom loader is created.
            }
        }
        return Class.forName(API_CLASS);
    }

    public record EventData(Material material, int amount, UUID farmerUuid, Method setAmountMethod, Method cancelMethod) {
        public void setAmount(Event event, int amount) throws ReflectiveOperationException {
            if (setAmountMethod != null) setAmountMethod.invoke(event, amount);
            else cancelMethod.invoke(event, true);
        }
    }

    public record OwnerData(UUID uuid, String name) {
        public static OwnerData unknown() { return new OwnerData(null, ""); }
        public boolean known() { return uuid != null; }
    }
}
