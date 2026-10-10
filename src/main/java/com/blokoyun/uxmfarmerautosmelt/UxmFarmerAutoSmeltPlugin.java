package com.blokoyun.uxmfarmerautosmelt;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class UxmFarmerAutoSmeltPlugin extends JavaPlugin implements CommandExecutor {
    private FarmerHook hook;
    private boolean retryScheduled;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadHook();
        if (getCommand("uxmfarmerotosmelter") != null) {
            getCommand("uxmfarmerotosmelter").setExecutor(this);
        }
        scheduleHookRetry();
    }

    @Override
    public void onDisable() {
        hook = null;
    }

    private void reloadHook() {
        reloadConfig();
        RuleConfiguration configuration = RuleConfiguration.load(this);
        if (hook == null) {
            hook = new FarmerHook(this, configuration);
        } else {
            hook.updateConfiguration(configuration);
        }
        if (!hook.register()) {
            scheduleHookRetry();
        }
    }

    private void scheduleHookRetry() {
        if (retryScheduled || hook == null) {
            return;
        }
        retryScheduled = true;
        getServer().getGlobalRegionScheduler().runDelayed(this, task -> {
            retryScheduled = false;
            if (hook != null && !hook.register()) {
                scheduleHookRetry();
            }
        }, 200L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("uxmfarmerotosmelter.admin")) {
            sender.sendMessage("§cBu komutu kullanmak için yetkin yok.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadHook();
            sender.sendMessage("§aUXMFarmer oto eritme ayarları yeniden yüklendi.");
            return true;
        }
        sender.sendMessage("§eKullanım: /" + label + " reload");
        return true;
    }
}
