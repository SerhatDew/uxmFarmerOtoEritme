package com.blokoyun.uxmfarmerautosmelt;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/** Uses only UXMFarmer public methods; an incompatible contract fails closed. */
final class FarmerStorage {
    private final Plugin dependency;
    private final Map<UUID, Object> boundFarmers = new HashMap<>();

    FarmerStorage(Plugin dependency) {
        this.dependency = dependency;
    }

    void bind(UUID token, Object farmer) {
        boundFarmers.put(token, farmer);
    }

    boolean canAccess(UUID farmerId, Player player) throws ReflectiveOperationException {
        Object farmer = farmer(farmerId);
        // The farmer's owner or an existing farmer member may use the configured permission.
        Object owner = farmer.getClass().getMethod("getOwnerUUID").invoke(farmer);
        if (player.getUniqueId().equals(owner)) {
            return true;
        }
        try {
            Object member = farmer.getClass().getMethod("isMember", Player.class).invoke(farmer, player);
            return Boolean.TRUE.equals(member);
        } catch (NoSuchMethodException ignored) {
            Object users = farmer.getClass().getMethod("getUsers").invoke(farmer);
            if (users instanceof Iterable<?> iterable) {
                for (Object user : iterable) {
                    Object uuid = user.getClass().getMethod("getUuid").invoke(user);
                    if (player.getUniqueId().equals(uuid)) return true;
                }
            }
            return false;
        }
    }

    int amount(UUID farmerId, Material material) throws ReflectiveOperationException {
        Object item = item(farmer(farmerId), material);
        return item == null ? 0 : number(item, "getAmount");
    }

    StockSnapshot snapshot(UUID farmerId, SmeltRecipe recipe) throws ReflectiveOperationException {
        ensureSingleServer();
        Object farmer = farmer(farmerId);
        Object source = item(farmer, recipe.rule().material());
        if (source == null) {
            throw new IllegalStateException("Farmer deposunda kaynak ürün yok");
        }
        checkUnlocked(source);
        StockSnapshot sourceSnapshot = new StockSnapshot(source, number(source, "getAmount"), null, 0, 0);
        if (recipe.rule().mode() == ItemRule.RuleMode.COMMANDS) {
            return sourceSnapshot;
        }
        Object destination = item(farmer, recipe.output());
        if (destination == null) {
            Class<?> api = Class.forName("com.uxplima.uxmfarmer.api.UXMFarmerAPI", true,
                    dependency.getClass().getClassLoader());
            Method create = api.getMethod("getOrCreateCollectedItem", farmer.getClass(), Material.class);
            destination = create.invoke(null, farmer, recipe.output());
        }
        checkUnlocked(destination);
        // Also verifies that UXMFarmer has a collected-materials entry for the result.
        int capacity;
        try {
            Object level = destination.getClass().getMethod("getCurrentLevel").invoke(destination);
            capacity = number(level, "getCapacity");
        } catch (NoSuchMethodException ignored) {
            Object inv = farmer.getClass().getMethod("getInv").invoke(farmer);
            capacity = number(inv, "getCapacity");
        }
        return new StockSnapshot(source, number(source, "getAmount"), destination,
                number(destination, "getAmount"), capacity);
    }

    private Object farmer(UUID id) throws ReflectiveOperationException {
        if (!dependency.isEnabled()) {
            throw new IllegalStateException("uxmFarmer etkin değil");
        }
        Object bound = boundFarmers.get(id);
        if (bound != null) return bound;
        Class<?> api = Class.forName("com.uxplima.uxmfarmer.api.UXMFarmerAPI", true,
                dependency.getClass().getClassLoader());
        Object result = api.getMethod("getByUUID", UUID.class).invoke(null, id);
        if (!(result instanceof Optional<?> optional) || optional.isEmpty()) {
            throw new IllegalStateException("Farmer bulunamadı");
        }
        return optional.get();
    }

    private void ensureSingleServer() throws ReflectiveOperationException {
        Object config;
        try {
            config = dependency.getClass().getMethod("getConfiguration").invoke(dependency);
        } catch (NoSuchMethodException ignored) {
            // Farmer v6 has no multi-server setting exposed through its public API.
            return;
        }
        Object multiServer = config.getClass().getMethod("getMultiServer").invoke(config);
        if (Boolean.TRUE.equals(multiServer.getClass().getMethod("isEnabled").invoke(multiServer))) {
            throw new IllegalStateException("Çok sunuculu UXMFarmer deposu için atomik işlem API'si gerekli");
        }
    }

    private static Object item(Object farmer, Material material) throws ReflectiveOperationException {
        try {
            Object inv = farmer.getClass().getMethod("getInv").invoke(farmer);
            Object items = inv.getClass().getMethod("getItems").invoke(inv);
            if (items instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    Object itemMaterial = item.getClass().getMethod("getMaterial").invoke(item);
                    if (itemMaterial != null && itemMaterial.toString().equalsIgnoreCase(material.name())) return item;
                }
            }
            return null;
        } catch (NoSuchMethodException ignored) {
            // UXMFarmer API variant below.
        }
        Object items = farmer.getClass().getMethod("getCollectedItems").invoke(farmer);
        if (!(items instanceof Map<?, ?> map)) {
            throw new IllegalStateException("UXMFarmer depo API'si uyumsuz");
        }
        return map.get(material);
    }

    private static void checkUnlocked(Object item) throws ReflectiveOperationException {
        if (Boolean.TRUE.equals(item.getClass().getMethod("isLocked").invoke(item))) {
            throw new IllegalStateException("Farmer ürün kaydı kilitli");
        }
    }

    private static int number(Object target, String method) throws ReflectiveOperationException {
        Object value = target.getClass().getMethod(method).invoke(target);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("UXMFarmer sayısal API değeri uyumsuz: " + method);
        }
        return number.intValue();
    }

    record StockSnapshot(Object source, int sourceAmount, Object destination, int destinationAmount,
                         int capacity) implements SmeltTransaction.Stock {
        @Override
        public int sourceAmount() { return sourceAmount; }

        @Override
        public int destinationSpace() {
            return Math.max(0, capacity - destinationAmount);
        }

        @Override
        public void debit(int amount) throws ReflectiveOperationException {
            set(source, sourceAmount - amount);
        }

        @Override
        public void credit(int amount) throws ReflectiveOperationException {
            set(destination, Math.addExact(destinationAmount, amount));
        }

        @Override
        public void restore() throws ReflectiveOperationException {
            if (destination != null) {
                set(destination, destinationAmount);
            }
            set(source, sourceAmount);
        }

        private static void set(Object item, int value) throws ReflectiveOperationException {
            try {
                item.getClass().getMethod("setAmount", int.class).invoke(item, value);
            } catch (NoSuchMethodException ignored) {
                item.getClass().getMethod("setAmount", long.class).invoke(item, (long) value);
            }
        }
    }
}
