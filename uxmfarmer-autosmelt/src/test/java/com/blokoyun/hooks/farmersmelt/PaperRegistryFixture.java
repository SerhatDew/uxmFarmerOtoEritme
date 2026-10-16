package com.blokoyun.hooks.farmersmelt;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.BlockType;
import org.bukkit.inventory.ItemType;

import java.lang.reflect.Proxy;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Material's item/block queries require Paper registries even in server-free tests. */
public final class PaperRegistryFixture {
    private PaperRegistryFixture() { }

    public static void initialize() throws ClassNotFoundException {
        RegistryAccess access = mock(RegistryAccess.class);
        try (var registryAccess = mockStatic(RegistryAccess.class)) {
            registryAccess.when(RegistryAccess::registryAccess).thenReturn(access);
            doAnswer(call -> registry(call.getArgument(0))).when(access).getRegistry(any(RegistryKey.class));
            doAnswer(call -> registry(call.getArgument(0))).when(access).getRegistry(any(Class.class));
            Class.forName("org.bukkit.Registry");
        }
    }

    private static Object registry(Object kind) {
        return Proxy.newProxyInstance(PaperRegistryFixture.class.getClassLoader(), new Class<?>[]{Registry.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("get") && args[0] instanceof NamespacedKey key) {
                        String type = String.valueOf(kind);
                        if (type.contains("item")) return typed(key, false);
                        if (type.contains("block")) return typed(key, true);
                    }
                    if (method.getName().equals("iterator")) return Collections.emptyIterator();
                    return null;
                });
    }

    private static Object typed(NamespacedKey key, boolean block) {
        Class<?> type = block ? BlockType.Typed.class : ItemType.Typed.class;
        return Proxy.newProxyInstance(PaperRegistryFixture.class.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "typed" -> proxy;
                    case "getKey" -> key;
                    case "isAir" -> key.getKey().equals("air");
                    case "hashCode" -> key.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> key.toString();
                    default -> null;
                });
    }
}
