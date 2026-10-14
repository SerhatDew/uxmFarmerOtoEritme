package com.blokoyun.uxmfarmerautosmelt;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class UxmFarmerAutoSmeltPlugin extends JavaPlugin implements CommandExecutor {
    private FarmerHook hook;
    private SmeltController controller;
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
        if (controller != null) {
            controller.shutdown();
        }
        if (hook != null) {
            hook.unregister();
        }
        smeltGuiHook = null;
        controller = null;
        hook = null;
    }

    private void reloadHook() {
        migrateConfiguration();
        reloadConfig();
        configuration = RuleConfiguration.load(this);
        if (smeltGuiHook != null) {
            smeltGuiHook.shutdown();
        }
        if (controller != null) {
            controller.shutdown();
        }
        if (hook == null) {
            hook = new FarmerHook(this, configuration);
        } else {
            hook.updateConfiguration(configuration);
        }
        hook.unregister();
        boolean hookReady = !configuration.automatic() || hook.register();
        controller = new SmeltController(this, configuration);
        boolean controllerReady = controller.register();
        smeltGuiHook = new SmeltGuiHook(this, configuration, controller);
        boolean guiReady = smeltGuiHook.register();
        if (!hookReady || !controllerReady || !guiReady) scheduleHookRetry();
    }

    private void migrateConfiguration() {
        File file = new File(getDataFolder(), "config.yml");
        YamlConfiguration disk = YamlConfiguration.loadConfiguration(file);
        if (ConfigurationMigration.upgrade(disk)) {
            try {
                disk.save(file);
                getLogger().info("Eritme config'i güncellendi; mevcut özel tarifler korundu.");
            } catch (IOException ex) {
                throw new IllegalStateException("Eritme config'i güncellenemedi", ex);
            }
        }
    }

    private void scheduleHookRetry() {
        if (retryScheduled || (hook == null && controller == null && smeltGuiHook == null)) {
            return;
        }
        retryScheduled = true;
        getServer().getGlobalRegionScheduler().runDelayed(this, task -> {
            retryScheduled = false;
            boolean hookReady = hook == null || !configuration.automatic() || hook.register();
            boolean controllerReady = controller == null || controller.register();
            boolean guiReady = smeltGuiHook == null || smeltGuiHook.register();
            if (!hookReady || !controllerReady || !guiReady) {
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
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            if (!configuration.enabled()) {
                PlayerMessages.send(sender, "§eᴏᴛᴏ ᴇʀɪᴛᴍᴇ ᴀʏᴀʀʟᴀʀᴅᴀ ᴋᴀᴘᴀʟɪ.");
            } else if (controller != null && controller.isRegistered()) {
                String automatic = !configuration.autoSmeltEnabled()
                        ? "§eᴏᴛᴏᴍᴀᴛɪᴋ ᴇʀɪᴛᴍᴇ ᴋᴀᴘᴀʟɪ"
                        : controller.automaticAvailable()
                        ? "§aᴏᴛᴏᴍᴀᴛɪᴋ ᴇʀɪᴛᴍᴇ ᴀᴋᴛɪꜰ"
                        : "§eᴏᴛᴏᴍᴀᴛɪᴋ ᴇʀɪᴛᴍᴇ ᴅᴇsᴛᴇᴋʟᴇɴᴍɪʏᴏʀ";
                String gui = smeltGuiHook != null && smeltGuiHook.isRegistered()
                        ? "§aGUI ᴀᴋᴛɪꜰ" : "§eGUI ᴋᴀᴘᴀʟɪ";
                PlayerMessages.send(sender, automatic + "§7, " + gui + ".");
            } else {
                PlayerMessages.send(sender, "§cꜰᴀʀᴍᴇʀ ʙᴀɢʟᴀɴᴛɪsɪ ᴋᴜʀᴜʟᴀᴍᴀᴅɪ. §7ᴋᴏɴsᴏʟᴅᴀᴋɪ ᴜʏᴀʀɪʏɪ ᴋᴏɴᴛʀᴏʟ ᴇᴛ.");
            }
            return true;
        }
        PlayerMessages.send(sender, "§eᴋᴜʟʟᴀɴɪᴍ: /" + label + " reload | status");
        return true;
    }
}
