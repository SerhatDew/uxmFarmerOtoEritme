package com.blokoyun.hooks.farmersmelt;

import org.bukkit.command.CommandSender;

public final class PlayerMessages {
    private static final String PREFIX = "§x§f§f§F§F§1§7§lʙʟᴏᴋᴏʏᴜɴ §8» ";

    private PlayerMessages() { }

    public static void send(CommandSender player, String message) {
        player.sendMessage(PREFIX + message);
    }
}
