package me.egg82.tfaplus.events;

import co.aikar.commands.CommandManager;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import me.egg82.tfaplus.enums.Message;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.services.CollectionProvider;
import me.egg82.tfaplus.utils.ConfigUtil;
import ninja.egg82.events.BukkitEventFilters;
import ninja.egg82.events.BukkitEvents;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;

public class FrozenEvents extends EventHolder {
    private final Plugin plugin;
    private final CommandManager commandManager;

    private final LoadingCache<UUID, Boolean> recentlyMoved = Caffeine.newBuilder().expireAfterWrite(10L, TimeUnit.SECONDS).build(k -> Boolean.FALSE);
    private final LoadingCache<UUID, Boolean> recentlyTeleported = Caffeine.newBuilder().expireAfterWrite(10L, TimeUnit.SECONDS).build(k -> Boolean.FALSE);

    public FrozenEvents(Plugin plugin, CommandManager commandManager) {
        this.plugin = plugin;
        this.commandManager = commandManager;

        // Interact/Block
        events.add(
                BukkitEvents.subscribe(plugin, PlayerInteractEvent.class, EventPriority.LOWEST)
                        .filter(BukkitEventFilters.ignoreCancelled())
                        .filter(e -> CollectionProvider.getFrozen().containsKey(e.getPlayer().getUniqueId()))
                        .handler(this::playerInteract)
        );

        events.add(
                BukkitEvents.subscribe(plugin, BlockPlaceEvent.class, EventPriority.LOWEST)
                        .filter(BukkitEventFilters.ignoreCancelled())
                        .filter(e -> CollectionProvider.getFrozen().containsKey(e.getPlayer().getUniqueId()))
                        .handler(this::blockPlace)
        );

        events.add(
                BukkitEvents.subscribe(plugin, BlockBreakEvent.class, EventPriority.LOWEST)
                        .filter(BukkitEventFilters.ignoreCancelled())
                        .filter(e -> CollectionProvider.getFrozen().containsKey(e.getPlayer().getUniqueId()))
                        .handler(this::blockBreak)
        );

        // Move/Teleport
        events.add(
                BukkitEvents.subscribe(plugin, PlayerMoveEvent.class, EventPriority.LOWEST)
                        .filter(BukkitEventFilters.ignoreCancelled())
                        .filter(e -> CollectionProvider.getFrozen().containsKey(e.getPlayer().getUniqueId()))
                        .filter(e -> areEqualXZ(e.getFrom(), e.getTo()) && e.getTo().getY() < e.getFrom().getY())
                        .handler(this::playerMove)
        );

        events.add(
                BukkitEvents.subscribe(plugin, PlayerTeleportEvent.class, EventPriority.LOWEST)
                        .filter(BukkitEventFilters.ignoreCancelled())
                        .filter(e -> CollectionProvider.getFrozen().containsKey(e.getPlayer().getUniqueId()))
                        .filter(e -> areEqualXZ(e.getFrom(), e.getTo()) && e.getTo().getY() < e.getFrom().getY())
                        .handler(this::playerTeleport)
        );

        // Drop/Pickup
        events.add(
                BukkitEvents.subscribe(plugin, PlayerDropItemEvent.class, EventPriority.LOWEST)
                        .filter(BukkitEventFilters.ignoreCancelled())
                        .filter(e -> CollectionProvider.getFrozen().containsKey(e.getPlayer().getUniqueId()))
                        .handler(this::playerDropItem)
        );

        try {
            Class.forName("org.bukkit.event.entity.EntityPickupItemEvent");
            events.add(
                    BukkitEvents.subscribe(plugin, EntityPickupItemEvent.class, EventPriority.LOWEST)
                            .filter(BukkitEventFilters.ignoreCancelled())
                            .filter(e -> e.getEntity() instanceof Player)
                            .filter(e -> CollectionProvider.getFrozen().containsKey(e.getEntity().getUniqueId()))
                            .handler(this::entityPickupItem)
            );
        } catch (ClassNotFoundException ignored) {
            events.add(
                    BukkitEvents.subscribe(plugin, PlayerPickupItemEvent.class, EventPriority.LOWEST)
                            .filter(BukkitEventFilters.ignoreCancelled())
                            .filter(e -> CollectionProvider.getFrozen().containsKey(e.getPlayer().getUniqueId()))
                            .handler(this::playerPickupItem)
            );
        }

        try {
            Class.forName("org.bukkit.event.player.PlayerPickupArrowEvent");
            events.add(
                    BukkitEvents.subscribe(plugin, PlayerPickupArrowEvent.class, EventPriority.LOWEST)
                            .filter(BukkitEventFilters.ignoreCancelled())
                            .filter(e -> CollectionProvider.getFrozen().containsKey(e.getPlayer().getUniqueId()))
                            .handler(this::playerPickupArrow)
            );
        } catch (ClassNotFoundException ignored) {}

        // Damage/Attack
        events.add(
                BukkitEvents.subscribe(plugin, EntityDamageByEntityEvent.class, EventPriority.LOWEST)
                        .filter(BukkitEventFilters.ignoreCancelled())
                        .filter(e -> e.getDamager() instanceof Player)
                        .filter(e -> CollectionProvider.getFrozen().containsKey(e.getDamager().getUniqueId()))
                        .handler(this::entityDamageByEntity)
        );

        events.add(
                BukkitEvents.subscribe(plugin, EntityDamageEvent.class, EventPriority.LOWEST)
                        .filter(BukkitEventFilters.ignoreCancelled())
                        .filter(e -> e.getEntity() instanceof Player)
                        .filter(e -> CollectionProvider.getFrozen().containsKey(e.getEntity().getUniqueId()))
                        .handler(this::entityDamage)
        );

        // Inventory
        events.add(
                BukkitEvents.subscribe(plugin, InventoryClickEvent.class, EventPriority.LOWEST)
                        .filter(BukkitEventFilters.ignoreCancelled())
                        .filter(e -> CollectionProvider.getFrozen().containsKey(e.getWhoClicked().getUniqueId()))
                        .handler(this::inventoryClick)
        );

        events.add(
                BukkitEvents.subscribe(plugin, InventoryDragEvent.class, EventPriority.LOWEST)
                        .filter(BukkitEventFilters.ignoreCancelled())
                        .filter(e -> CollectionProvider.getFrozen().containsKey(e.getWhoClicked().getUniqueId()))
                        .handler(this::inventoryDrag)
        );

        events.add(
                BukkitEvents.subscribe(plugin, InventoryMoveItemEvent.class, EventPriority.LOWEST)
                        .filter(BukkitEventFilters.ignoreCancelled())
                        .filter(e -> e.getSource().getHolder() instanceof Player)
                        .filter(e -> CollectionProvider.getFrozen().containsKey(((Player) e.getSource().getHolder()).getUniqueId()))
                        .handler(this::inventoryMoveItem)
        );
    }

    private void playerInteract(PlayerInteractEvent event) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        if (cachedConfig.get().getFreeze().getInteract()) {
            commandManager.getCommandIssuer(event.getPlayer()).sendError(Message.ERROR__NEED_AUTH_ACTION);
            event.setCancelled(true);
        }
    }

    private void blockPlace(BlockPlaceEvent event) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        if (cachedConfig.get().getFreeze().getBlocks()) {
            commandManager.getCommandIssuer(event.getPlayer()).sendError(Message.ERROR__NEED_AUTH_ACTION);
            event.setCancelled(true);
        }
    }

    private void blockBreak(BlockBreakEvent event) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        if (cachedConfig.get().getFreeze().getBlocks()) {
            commandManager.getCommandIssuer(event.getPlayer()).sendError(Message.ERROR__NEED_AUTH_ACTION);
            event.setCancelled(true);
        }
    }

    private void playerMove(PlayerMoveEvent event) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        if (cachedConfig.get().getFreeze().getMove()) {
            if (!recentlyMoved.get(event.getPlayer().getUniqueId())) {
                recentlyMoved.put(event.getPlayer().getUniqueId(), Boolean.TRUE);
                commandManager.getCommandIssuer(event.getPlayer()).sendError(Message.ERROR__NEED_AUTH_MOVE);
            }
            event.setCancelled(true);
        }
    }

    private void playerTeleport(PlayerTeleportEvent event) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        if (cachedConfig.get().getFreeze().getMove()) {
            if (!recentlyTeleported.get(event.getPlayer().getUniqueId())) {
                recentlyTeleported.put(event.getPlayer().getUniqueId(), Boolean.TRUE);
                commandManager.getCommandIssuer(event.getPlayer()).sendError(Message.ERROR__NEED_AUTH_MOVE);
            }
            event.setCancelled(true);
        }
    }

    private static boolean areEqualXZ(Location from, Location to) { return from.getWorld().equals(to.getWorld()) && from.getX() == to.getX() && from.getZ() == to.getZ(); }

    private void playerDropItem(PlayerDropItemEvent event) {
        Optional<CachedConfigValues> cachedConfig= ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        if (cachedConfig.get().getFreeze().getDrops()) {
            commandManager.getCommandIssuer(event.getPlayer()).sendError(Message.ERROR__NEED_AUTH_ACTION);
            event.setCancelled(true);
        }
    }

    private void entityPickupItem(EntityPickupItemEvent event) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        if (cachedConfig.get().getFreeze().getDrops()) {
            event.setCancelled(true);
        }
    }

    private void playerPickupItem(PlayerPickupItemEvent event) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        if (cachedConfig.get().getFreeze().getDrops()) {
            event.setCancelled(true);
        }
    }

    private void playerPickupArrow(PlayerPickupArrowEvent event) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        if (cachedConfig.get().getFreeze().getDrops()) {
            event.setCancelled(true);
        }
    }

    private void entityDamageByEntity(EntityDamageByEntityEvent event) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        if (cachedConfig.get().getFreeze().getAttack()) {
            commandManager.getCommandIssuer(event.getDamager()).sendError(Message.ERROR__NEED_AUTH_ACTION);
            event.setCancelled(true);
        }
    }

    private void entityDamage(EntityDamageEvent event) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        if (cachedConfig.get().getFreeze().getDamage()) {
            event.setCancelled(true);
        }
    }

    private void inventoryClick(InventoryClickEvent event) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        if (cachedConfig.get().getFreeze().getInventory()) {
            commandManager.getCommandIssuer(event.getWhoClicked()).sendError(Message.ERROR__NEED_AUTH_ACTION);
            event.setCancelled(true);
        }
    }

    private void inventoryDrag(InventoryDragEvent event) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        if (cachedConfig.get().getFreeze().getInventory()) {
            commandManager.getCommandIssuer(event.getWhoClicked()).sendError(Message.ERROR__NEED_AUTH_ACTION);
            event.setCancelled(true);
        }
    }

    private void inventoryMoveItem(InventoryMoveItemEvent event) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        if (cachedConfig.get().getFreeze().getInventory()) {
            commandManager.getCommandIssuer(event.getSource().getHolder()).sendError(Message.ERROR__NEED_AUTH_ACTION);
            event.setCancelled(true);
        }
    }
}
