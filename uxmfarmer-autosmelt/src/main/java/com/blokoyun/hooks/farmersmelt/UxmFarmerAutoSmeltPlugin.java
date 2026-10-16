package com.blokoyun.hooks.farmersmelt;
import com.blokoyun.hooks.farmersmelt.config.ConfigurationMigration;
import com.blokoyun.hooks.farmersmelt.config.RuleConfiguration;
import com.blokoyun.hooks.farmersmelt.listener.FarmerHook;
import com.blokoyun.hooks.farmersmelt.listener.SmeltGuiHook;
import com.blokoyun.hooks.farmersmelt.smelt.SmeltController;
import com.blokoyun.hooks.common.logging.LifecycleLogger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class UxmFarmerAutoSmeltPlugin extends JavaPlugin implements CommandExecutor {
    private final LifecycleLogger lifecycleLogger = new LifecycleLogger(this);
    private FarmerHook hook;
    private SmeltController controller;
    private SmeltGuiHook smeltGuiHook;
    private RuleConfiguration configuration;
    private BukkitTask retryTask;
    private boolean accepting;
    private long startupNanos;
    private boolean startupReported;

    @Override
    public void onEnable() {
        startupNanos = System.nanoTime();
        startupReported = false;
        lifecycleLogger.startup();
        try {
            saveDefaultConfig();
            reloadHook();
            if (getCommand("uxmfarmerotosmelter") != null) {
                getCommand("uxmfarmerotosmelter").setExecutor(this);
            }
            if (isFullyReady()) {
                startupReported = true;
                lifecycleLogger.started(startupNanos);
            } else {
                lifecycleLogger.info("UXMFarmer bağımlılığı bekleniyor; bileşenler hazır olduğunda yeniden denenecek.");
            }
        } catch (RuntimeException | LinkageError ex) {
            accepting = false;
            startupReported = false;
            lifecycleLogger.failure("Oto eritme başlatılamadı; eklenti hazır değil.", ex);
            cleanupResources();
            throw ex;
        }
    }

    @Override
    public void onDisable() {
        long stoppedNanos = System.nanoTime();
        lifecycleLogger.shutdown();
        accepting = false;
        boolean clean = cleanupResources();
        if (clean && startupReported) {
            lifecycleLogger.stopped(stoppedNanos);
        } else {
            lifecycleLogger.info(clean ? "Eklenti hazır hale gelmeden devre dışı bırakıldı."
                    : "Kapanış sırasında hata oluştu; ayrıntılar yukarıda.");
        }
    }

    private boolean isFullyReady() {
        return configuration != null && (!configuration.enabled()
                || ((!configuration.automatic() || hook != null && hook.isRegistered())
                && controller != null && controller.isRegistered()
                && (!configuration.guiSettings().enabled() || smeltGuiHook != null && smeltGuiHook.isRegistered())));
    }

    private void reloadHook() {
        RuleConfiguration loaded = loadConfiguration();
        accepting = false;
        if (!cleanupResources()) {
            throw new IllegalStateException("Önceki eritme kaynakları tamamen kapatılamadı.");
        }
        startupReported = false;
        configuration = loaded;
        accepting = true;
        lifecycleLogger.info("Yapılandırma yüklendi.");
        if (!loaded.enabled()) {
            lifecycleLogger.info("Eritme yapılandırmadan kapalı; dinleyici ve görev başlatılmadı.");
            return;
        }
        FarmerHook nextHook = new FarmerHook(this, loaded);
        SmeltController nextController = new SmeltController(this, loaded);
        SmeltGuiHook nextGui = new SmeltGuiHook(this, loaded, nextController);
        configuration = loaded;
        hook = nextHook;
        controller = nextController;
        smeltGuiHook = nextGui;
        accepting = true;
        boolean hookReady = !loaded.automatic() || nextHook.register();
        boolean controllerReady = nextController.register();
        boolean guiReady = nextGui.register();
        if (!hookReady || !controllerReady || !guiReady) {
            scheduleHookRetry();
        }
    }

    private RuleConfiguration loadConfiguration() {
        File file = new File(getDataFolder(), "config.yml");
        YamlConfiguration disk = new YamlConfiguration();
        disk.options().parseComments(true);
        try {
            disk.load(file);
            boolean migrated = ConfigurationMigration.upgrade(disk);
            RuleConfiguration loaded = RuleConfiguration.load(disk, getLogger());
            if (migrated) {
                disk.save(file);
                lifecycleLogger.info("Eritme config'i güncellendi; mevcut özel tarifler korundu.");
            }
            return loaded;
        } catch (IOException | InvalidConfigurationException ex) {
            throw new IllegalStateException("Eritme config'i okunamadı veya taşınamadı.", ex);
        }
    }

    private void scheduleHookRetry() {
        if (retryTask != null || !accepting) return;
        retryTask = getServer().getScheduler().runTaskLater(this, () -> {
            retryTask = null;
            if (!accepting || configuration == null) return;
            try {
                boolean hookReady = !configuration.automatic() || hook != null && hook.register();
                boolean controllerReady = controller != null && controller.register();
                boolean guiReady = smeltGuiHook != null && smeltGuiHook.register();
                if (!hookReady || !controllerReady || !guiReady) {
                    scheduleHookRetry();
                } else if (!startupReported) {
                    startupReported = true;
                    lifecycleLogger.info("UXMFarmer bağlantısı hazır; oto eritme bileşenleri aktif.");
                    lifecycleLogger.started(startupNanos);
                }
            } catch (RuntimeException | LinkageError ex) {
                startupReported = false;
                accepting = false;
                lifecycleLogger.failure("UXMFarmer bağlantısı başlatılamadı.", ex);
                cleanupResources();
            }
        }, 200L);
    }

    private boolean cancelRetry() {
        if (retryTask == null) return true;
        try {
            retryTask.cancel();
            return true;
        } catch (RuntimeException | LinkageError ex) {
            lifecycleLogger.failure("Yeniden deneme görevi iptal edilemedi.", ex);
            return false;
        } finally {
            retryTask = null;
        }
    }

    private boolean cleanupResources() {
        accepting = false;
        boolean clean = cancelRetry();
        clean = cleanupComponents() && clean;
        configuration = null;
        return clean;
    }

    private boolean cleanupComponents() {
        boolean clean = true;
        if (smeltGuiHook != null) {
            try {
                clean = smeltGuiHook.shutdown() && clean;
            } catch (RuntimeException | LinkageError ex) {
                clean = false;
                lifecycleLogger.failure("GUI temizlenemedi.", ex);
            }
        }
        if (controller != null) {
            try {
                clean = controller.shutdown() && clean;
            } catch (RuntimeException | LinkageError ex) {
                clean = false;
                lifecycleLogger.failure("Eritme denetleyicisi temizlenemedi.", ex);
            }
        }
        if (hook != null) {
            try {
                hook.unregister();
            } catch (RuntimeException | LinkageError ex) {
                clean = false;
                lifecycleLogger.failure("UXMFarmer hook'u temizlenemedi.", ex);
            }
        }
        smeltGuiHook = null;
        controller = null;
        hook = null;
        return clean;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("uxmfarmerotosmelter.admin")) {
            PlayerMessages.send(sender, "§cʙᴜ ᴋᴏᴍᴜᴛ ɪᴄɪɴ ʏᴇᴛᴋɪɴ ʏᴏᴋ.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            try {
                startupNanos = System.nanoTime();
                reloadHook();
                if (isFullyReady()) {
                    startupReported = true;
                    lifecycleLogger.started(startupNanos);
                    PlayerMessages.send(sender, "§aʙᴀsᴀʀɪʟɪ ʏᴇɴɪʟᴇɴᴅɪ.");
                } else {
                    PlayerMessages.send(sender, "§eʙᴀɢʟᴀɴᴛɪʟᴀʀ ʙᴇᴋʟᴇɴɪʏᴏʀ; ʏᴇɴɪᴅᴇɴ ᴅᴇɴᴇɴᴇᴄᴇᴋ.");
                }
            } catch (RuntimeException | LinkageError ex) {
                accepting = false;
                startupReported = false;
                cleanupResources();
                lifecycleLogger.failure("Yapılandırma yeniden yüklenemedi.", ex);
                PlayerMessages.send(sender, "§cʏᴇɴɪʟᴇᴍᴇ ʙᴀsᴀʀɪsɪᴢ; ᴇᴋʟᴇɴᴛɪ ᴘᴀsɪꜰ.");
            }
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            if (configuration == null || !accepting) {
                PlayerMessages.send(sender, "§cᴇᴋʟᴇɴᴛɪ ʜᴀᴢɪʀ ᴅᴇɢɪʟ.");
            } else if (!configuration.enabled()) {
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
