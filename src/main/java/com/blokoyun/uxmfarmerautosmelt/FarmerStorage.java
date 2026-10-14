package com.blokoyun.uxmfarmerautosmelt;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Uses only UXMFarmer public methods; an incompatible contract fails closed. */
final class FarmerStorage {
    private final Plugin dependency;
    private final Map<UUID, Object> boundFarmers = new HashMap<>();
    private boolean repositoryResolved;
    private boolean modernAvailable;
    private Object repository;
    private Method entriesMethod;
    private Method entryMethod;

    FarmerStorage(Plugin dependency) {
        this.dependency = dependency;
    }

    void bind(UUID token, Object farmer) {
        if (token == null || farmer == null) return;
        try {
            initializeRepository();
            if (modernAvailable) return;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Keep legacy GUI binding only when the modern repository is unavailable.
        }
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

    void consume(UUID farmerId, Material material, int amount) throws ReflectiveOperationException {
        if (amount <= 0) return;
        Object item = item(farmer(farmerId), material);
        if (item == null || number(item, "getAmount") < amount) throw new IllegalStateException("Farmer deposu tüketim için yetersiz");
        try { item.getClass().getMethod("removeAmount", int.class).invoke(item, amount); }
        catch (NoSuchMethodException ignored) { set(item, number(item, "getAmount") - amount); }
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

    boolean automaticAvailable() {
        try {
            initializeRepository();
            return modernAvailable;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    Iterable<?> farmers() throws ReflectiveOperationException {
        initializeRepository();
        if (!modernAvailable) {
            throw new IllegalStateException("Modern FarmerRepository API kullanılamıyor");
        }
        Object entries = entriesMethod.invoke(repository);
        if (!(entries instanceof Iterable<?> iterable)) {
            throw new IllegalStateException("FarmerRepository.getEntries() koleksiyon döndürmedi");
        }
        return iterable;
    }

    private Object farmer(UUID id) throws ReflectiveOperationException {
        if (!dependency.isEnabled()) {
            throw new IllegalStateException("uxmFarmer etkin değil");
        }
        initializeRepository();
        if (modernAvailable) {
            Object live = lookupRepository(id);
            if (live != null) return live;
            throw new IllegalStateException("Farmer bulunamadı");
        }
        Object bound = boundFarmers.get(id);
        if (bound != null) return bound;
        throw new IllegalStateException("Farmer bulunamadı");
    }

    private void initializeRepository() throws ReflectiveOperationException {
        if (repositoryResolved) return;
        ClassLoader loader = dependency.getClass().getClassLoader();
        Class<?> pluginType;
        try {
            pluginType = Class.forName("com.uxplima.uxmfarmer.FarmerPlugin", true, loader);
        } catch (ClassNotFoundException ignored) {
            repositoryResolved = true;
            return;
        }
        if (!pluginType.isInstance(dependency)) {
            repositoryResolved = true;
            return;
        }
        Object database = pluginType.getMethod("getDatabaseManager").invoke(dependency);
        repository = database.getClass().getMethod("getFarmerRepository").invoke(database);
        entriesMethod = repository.getClass().getMethod("getEntries");
        try {
            entryMethod = repository.getClass().getMethod("getEntry", UUID.class);
        } catch (NoSuchMethodException erasedGenericId) {
            try {
                entryMethod = repository.getClass().getMethod("getEntry", Object.class);
            } catch (NoSuchMethodException erasedObjectId) {
                entryMethod = null;
            }
        }
        modernAvailable = true;
        repositoryResolved = true;
    }

    private Object lookupRepository(UUID id) throws ReflectiveOperationException {
        initializeRepository();
        if (!modernAvailable) return null;
        if (entryMethod != null) {
            Object result = entryMethod.invoke(repository, id);
            if (result instanceof Optional<?> optional && optional.isPresent()) return optional.get();
            return null;
        }
        Object entries = entriesMethod.invoke(repository);
        if (entries instanceof Iterable<?> iterable) {
            for (Object candidate : iterable) {
                if (candidate != null && id.equals(candidate.getClass().getMethod("getId").invoke(candidate))) {
                    return candidate;
                }
            }
        }
        return null;
    }

    UUID owner(UUID farmerId) throws ReflectiveOperationException {
        Object farmer = farmer(farmerId);
        Object owner = farmer.getClass().getMethod("getOwnerUUID").invoke(farmer);
        return owner instanceof UUID uuid ? uuid : null;
    }

    private void ensureSingleServer() throws ReflectiveOperationException {
        Object config;
        try {
            config = dependency.getClass().getMethod("getConfiguration").invoke(dependency);
        } catch (NoSuchMethodException ignored) {
            // Farmer variants without a public multi-server setting are single-server here.
            return;
        }
        if (config == null) return;
        Object multiServer = config.getClass().getMethod("getMultiServer").invoke(config);
        if (multiServer != null
                && Boolean.TRUE.equals(multiServer.getClass().getMethod("isEnabled").invoke(multiServer))) {
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

    private static void set(Object item, int value) throws ReflectiveOperationException {
        try { item.getClass().getMethod("setAmount", int.class).invoke(item, value); }
        catch (NoSuchMethodException ignored) { item.getClass().getMethod("setAmount", long.class).invoke(item, (long) value); }
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
