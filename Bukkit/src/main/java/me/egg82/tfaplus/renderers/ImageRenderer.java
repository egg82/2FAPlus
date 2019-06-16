package me.egg82.tfaplus.renderers;

import java.awt.image.BufferedImage;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

/**
 * Largely taken from SecureMyAccount
 * https://github.com/games647/SecureMyAccount/blob/master/src/main/java/com/github/games647/securemyaccount/ImageRenderer.java
 */

public class ImageRenderer extends MapRenderer {
    private final UUID playerUUID;
    private BufferedImage image;

    public ImageRenderer(UUID playerUUID, BufferedImage image) {
        super(true);

        this.playerUUID = playerUUID;
        this.image = image;
    }

    @Override
    public void render(MapView mapView, MapCanvas mapCanvas, Player player) {
        if (mapView == null) {
            throw new IllegalArgumentException("mapView cannot be null.");
        }
        if (mapCanvas == null) {
            throw new IllegalArgumentException("mapCanvas cannot be null.");
        }
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null.");
        }

        if (player.getUniqueId().equals(playerUUID)) {
            mapCanvas.drawImage(0, 0, image);
        }
    }
}
