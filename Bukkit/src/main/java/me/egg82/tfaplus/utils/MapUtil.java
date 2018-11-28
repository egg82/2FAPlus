package me.egg82.tfaplus.utils;

import java.awt.image.BufferedImage;
import me.egg82.tfaplus.renderers.ImageRenderer;
import me.egg82.tfaplus.renderers.QRRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.map.MapView;

/**
 * Largely taken from SecureMyAccount
 * https://github.com/games647/SecureMyAccount/blob/master/src/main/java/com/github/games647/securemyaccount/MapGiver.java
 */

public class MapUtil {
    private MapUtil() {}

    public static void sendTOTPQRCode(Player player, String key, String issuer, long codeLength) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null.");
        }

        BufferedImage image = QRRenderer.getTOTPImage(player.getName(), getServerName(), key, issuer, codeLength);
        MapView view = getView(player, image);

        ItemStack map = new ItemStack(Material.MAP, 1, (short) view.getId());
        setName(map, "TOTP code - DESTROY AFTER USE");
        player.getInventory().addItem(map);
    }

    public static void sendHOTPQRCode(Player player, String key, String issuer, long codeLength, long counter) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null.");
        }

        BufferedImage image = QRRenderer.getHOTPImage(player.getName(), getServerName(), key, issuer, codeLength, counter);
        MapView view = getView(player, image);

        ItemStack map = new ItemStack(Material.MAP, 1, (short) view.getId());
        setName(map, "HOTP code - DESTROY AFTER USE");
        player.getInventory().addItem(map);
    }

    private static MapView getView(Player player, BufferedImage image) {
        MapView retVal = Bukkit.createMap(player.getWorld());
        retVal.getRenderers().forEach(retVal::removeRenderer);

        retVal.addRenderer(new ImageRenderer(player.getUniqueId(), image));
        return retVal;
    }

    private static String getServerName() {
        String name = Bukkit.getServerName();
        if (name == null || name.isEmpty() || name.equalsIgnoreCase("unnamed") || name.equalsIgnoreCase("unknown") || name.equalsIgnoreCase("default") || name.startsWith("Unknown")) {
            return "Unknown";
        }
        return name;
    }

    private static void setName(ItemStack stack, String name) {
        ItemMeta meta = stack.hasItemMeta() ? stack.getItemMeta() : Bukkit.getItemFactory().getItemMeta(stack.getType());
        meta.setDisplayName(name);
        stack.setItemMeta(meta);
    }
}
