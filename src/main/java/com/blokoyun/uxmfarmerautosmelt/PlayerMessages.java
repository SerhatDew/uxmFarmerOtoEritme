package com.blokoyun.uxmfarmerautosmelt;

import org.bukkit.command.CommandSender;

final class PlayerMessages {
    private static final String PREFIX = "§x§f§f§F§F§1§7§lʙʟᴏᴋᴏʏᴜɴ §8» ";

    private PlayerMessages() { }

    static void send(CommandSender player, String message) {
        player.sendMessage(PREFIX + message);
    }
}
