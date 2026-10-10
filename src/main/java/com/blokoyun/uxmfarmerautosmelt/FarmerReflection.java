package com.blokoyun.uxmfarmerautosmelt;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

final class FarmerReflection {
    private static final String API_CLASS = "com.uxplima.uxmfarmer.api.UXMFarmerAPI";

    private FarmerReflection() {
    }

    static EventData readEvent(Event event) throws ReflectiveOperationException {
        Method materialMethod = event.getClass().getMethod("getCollectedProduct");
        Method amountMethod = event.getClass().getMethod("getCollectedAmount");
        Method farmerUuidMethod = event.getClass().getMethod("getFarmerUUID");
        Method setAmountMethod = event.getClass().getMethod("setCollectedAmount", int.class);
        Object material = materialMethod.invoke(event);
        Object amount = amountMethod.invoke(event);
        Object farmerUuid = farmerUuidMethod.invoke(event);
        if (!(material instanceof Material) || !(amount instanceof Number) || !(farmerUuid instanceof UUID)) {
            throw new IllegalStateException("UXMFarmer event getter dönüş tipleri beklenen türde değil");
        }
        return new EventData((Material) material, ((Number) amount).intValue(), (UUID) farmerUuid, setAmountMethod);
    }

    static OwnerData resolveOwner(UUID farmerUuid, JavaPlugin plugin, ClassLoader dependencyClassLoader) {
        try {
            Class<?> api = loadApiClass(plugin, dependencyClassLoader);
            Method getByUuid = api.getMethod("getByUUID", UUID.class);
            Object result = getByUuid.invoke(null, farmerUuid);
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) {
                return OwnerData.unknown();
            }
            Object farmer = optional.get();
            Method ownerMethod = farmer.getClass().getMethod("getOwnerUUID");
            Object ownerValue = ownerMethod.invoke(farmer);
            if (!(ownerValue instanceof UUID ownerUuid)) {
                return OwnerData.unknown();
            }
            OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUuid);
            String name = owner.getName();
            return new OwnerData(ownerUuid, name == null || name.isBlank() ? ownerUuid.toString() : name);
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().fine("UXMFarmer sahibine reflection ile ulaşılamadı: " + ex.getMessage());
            return OwnerData.unknown();
        }
    }

    private static Class<?> loadApiClass(JavaPlugin plugin, ClassLoader dependencyClassLoader)
            throws ClassNotFoundException {
        ClassLoader[] classLoaders = {
                dependencyClassLoader,
                plugin.getClass().getClassLoader(),
                Thread.currentThread().getContextClassLoader()
        };
        for (ClassLoader classLoader : classLoaders) {
            if (classLoader == null) {
                continue;
            }
            try {
                return Class.forName(API_CLASS, false, classLoader);
            } catch (ClassNotFoundException ignored) {
                // Bir sonraki classloader denenir.
            }
        }
        return Class.forName(API_CLASS);
    }

    record EventData(Material material, int amount, UUID farmerUuid, Method setAmountMethod) {
        void setAmount(Event event, int amount) throws ReflectiveOperationException {
            setAmountMethod.invoke(event, amount);
        }
    }

    record OwnerData(UUID uuid, String name) {
        static OwnerData unknown() {
            return new OwnerData(null, "");
        }

        boolean known() {
            return uuid != null;
        }
    }
}
