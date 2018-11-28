package me.egg82.tfaplus.utils;

import org.bukkit.ChatColor;

public class LogUtil {
    private LogUtil() {}

    public static String getHeading() { return ChatColor.YELLOW + "[" + ChatColor.AQUA + "2FA+" + ChatColor.YELLOW + "] " + ChatColor.RESET; }
}
