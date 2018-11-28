package me.egg82.tfaplus.utils;

import java.awt.image.BufferedImage;
import java.util.Map;
import me.egg82.tfaplus.renderers.ImageRenderer;
import me.egg82.tfaplus.renderers.QRRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

/**
 * Largely taken from SecureMyAccount
 * https://github.com/games647/SecureMyAccount/blob/master/src/main/java/com/github/games647/securemyaccount/MapGiver.java
 */

public class MapUtil {
    private MapUtil() {}

    private static final Material MAP;
    static {
        Material mapMaterial;

        try {
            mapMaterial = Material.valueOf("FILLED_MAP");
        } catch (IllegalArgumentException ignored) {
            mapMaterial = Material.valueOf("MAP");
        }

        MAP = mapMaterial;
    }

    public static void sendTOTPQRCode(Player player, String key, String issuer, long codeLength) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null.");
        }

        BufferedImage image = QRRenderer.getTOTPImage(player.getName(), getServerName(), key, issuer, codeLength);
        MapView view = getView(player, image);

        ItemStack map;
        if (BukkitVersionUtil.isAtLeast("1.13")) {
            map = new ItemStack(MAP, 1);
        } else {
            map = new ItemStack(MAP, 1, (short) view.getId());
        }
        setData(map, "TOTP code - DESTROY AFTER USE", view);
        ItemStack oldItem;
        if (BukkitVersionUtil.isAtLeast("1.9")) {
            oldItem = player.getInventory().getItemInMainHand();
            player.getInventory().setItemInMainHand(map);
        } else {
            oldItem = player.getInventory().getItemInHand();
            player.getInventory().setItemInHand(map);
        }
        if (oldItem != null && oldItem.getType() != Material.AIR) {
            Map<Integer, ItemStack> dropped = player.getInventory().addItem(oldItem);
            for (Map.Entry<Integer, ItemStack> kvp : dropped.entrySet()) {
                player.getWorld().dropItemNaturally(player.getLocation(), kvp.getValue());
            }
        }
    }

    public static void sendHOTPQRCode(Player player, String key, String issuer, long codeLength, long counter) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null.");
        }

        BufferedImage image = QRRenderer.getHOTPImage(player.getName(), getServerName(), key, issuer, codeLength, counter);
        MapView view = getView(player, image);

        ItemStack map;
        if (BukkitVersionUtil.isAtLeast("1.13")) {
            map = new ItemStack(MAP, 1);
        } else {
            map = new ItemStack(MAP, 1, (short) view.getId());
        }
        setData(map, "HOTP code - DESTROY AFTER USE", view);
        ItemStack oldItem;
        if (BukkitVersionUtil.isAtLeast("1.9")) {
            oldItem = player.getInventory().getItemInMainHand();
            player.getInventory().setItemInMainHand(map);
        } else {
            oldItem = player.getInventory().getItemInHand();
            player.getInventory().setItemInHand(map);
        }
        if (oldItem != null && oldItem.getType() != Material.AIR) {
            Map<Integer, ItemStack> dropped = player.getInventory().addItem(oldItem);
            for (Map.Entry<Integer, ItemStack> kvp : dropped.entrySet()) {
                player.getWorld().dropItemNaturally(player.getLocation(), kvp.getValue());
            }
        }
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

    private static void setData(ItemStack stack, String name, MapView view) {
        MapMeta meta = (MapMeta) (stack.hasItemMeta() ? stack.getItemMeta() : Bukkit.getItemFactory().getItemMeta(stack.getType()));
        if (BukkitVersionUtil.isAtLeast("1.13")) {
            meta.setMapId(view.getId());
        }
        meta.setDisplayName(name);
        stack.setItemMeta(meta);
    }
}
