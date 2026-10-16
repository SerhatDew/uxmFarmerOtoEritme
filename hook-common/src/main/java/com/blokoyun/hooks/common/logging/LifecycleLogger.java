package com.blokoyun.hooks.common.logging;

import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

/** Shared lifecycle output for the independently shipped Paper hooks. */
public final class LifecycleLogger {
    private final JavaPlugin plugin;

    public LifecycleLogger(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void startup() {
        printBanner(null);
    }

    public void shutdown() {
        printBanner("Eklenti kapatılıyor...");
    }

    public void info(String text) {
        plugin.getLogger().info(text);
    }

    public void failure(String text, Throwable failure) {
        plugin.getLogger().log(Level.SEVERE, text, failure);
    }

    public void started(long startedNanos) {
        info("Başarıyla başlatıldı. Süre: " + elapsedMillis(startedNanos) + " ms.");
    }

    public void stopped(long startedNanos) {
        info("Başarıyla devre dışı bırakıldı. Süre: " + elapsedMillis(startedNanos) + " ms.");
    }

    private void printBanner(String status) {
        String pluginVersion = plugin.getPluginMeta().getVersion();
        String platform = plugin.getServer().getName() + " " + plugin.getServer().getMinecraftVersion();
        String[] lines = {
            "",
            "   &6._______.",
            "   &6| o   o |    &6&l" + plugin.getName() + " &ev" + pluginVersion,
            "   &6|===o===|    &7" + (status == null ? "Platform: " + platform : status),
            "   &6/| \\_/ |\\",
            "   &6\\|_>o<_|/",
            ""
        };
        for (String line : lines) {
            plugin.getServer().getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', line));
        }
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}
