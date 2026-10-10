package com.blokoyun.uxmfarmerautosmelt;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class UxmFarmerAutoSmeltPlugin extends JavaPlugin implements CommandExecutor {
    private FarmerHook hook;
    private SmeltGuiHook smeltGuiHook;
    private RuleConfiguration configuration;
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
        if (smeltGuiHook != null) {
            smeltGuiHook.shutdown();
        }
        if (hook != null) {
            hook.unregister();
        }
        hook = null;
    }

    private void reloadHook() {
        reloadConfig();
        configuration = RuleConfiguration.load(this);
        if (hook == null) {
            hook = new FarmerHook(this, configuration);
        } else {
            hook.updateConfiguration(configuration);
        }
        hook.unregister();
        boolean hookReady = !configuration.automatic() || hook.register();
        if (smeltGuiHook != null) {
            smeltGuiHook.shutdown();
        }
        smeltGuiHook = new SmeltGuiHook(this, configuration);
        boolean guiReady = smeltGuiHook.register();
        if (!hookReady || !guiReady) scheduleHookRetry();
    }

    private void scheduleHookRetry() {
        if (retryScheduled || hook == null) {
            return;
        }
        retryScheduled = true;
        getServer().getGlobalRegionScheduler().runDelayed(this, task -> {
            retryScheduled = false;
            boolean hookReady = hook == null || !configuration.automatic() || hook.register();
            boolean guiReady = smeltGuiHook == null || smeltGuiHook.register();
            if (!hookReady || !guiReady) {
                scheduleHookRetry();
            }
        }, 200L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("uxmfarmerotosmelter.admin")) {
            PlayerMessages.send(sender, "§cʙᴜ ᴋᴏᴍᴜᴛ ɪᴄɪɴ ʏᴇᴛᴋɪɴ ʏᴏᴋ.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadHook();
            PlayerMessages.send(sender, "§aᴜxᴍғᴀʀᴍᴇʀ ᴏᴛᴏ ᴇʀɪᴛᴍᴇ ᴀʏᴀʀʟᴀʀɪ ʏᴇɴɪʟᴇɴᴅɪ.");
            return true;
        }
        PlayerMessages.send(sender, "§eᴋᴜʟʟᴀɴɪᴍ: /" + label + " reload");
        return true;
    }
}
