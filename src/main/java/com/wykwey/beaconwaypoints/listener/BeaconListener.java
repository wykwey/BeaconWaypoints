package com.wykwey.beaconwaypoints.listener;

import com.wykwey.beaconwaypoints.BeaconWaypoints;
import com.wykwey.beaconwaypoints.config.LangManager;
import com.wykwey.beaconwaypoints.config.PluginConfig;
import com.wykwey.beaconwaypoints.gui.BeaconMainMenu;
import com.wykwey.beaconwaypoints.waypoint.TeleportManager;
import com.wykwey.beaconwaypoints.waypoint.Waypoint;
import com.wykwey.beaconwaypoints.waypoint.WaypointManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.Event;
import org.bukkit.inventory.EquipmentSlot;

import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;

/** Handles registered beacon interaction and ownership protection. */
public final class BeaconListener implements Listener {

    private final WaypointManager waypointManager;
    private final TeleportManager teleportManager;
    private final PluginConfig config;
    private final LangManager lang;

    public BeaconListener(WaypointManager waypointManager, TeleportManager teleportManager,
                          PluginConfig config, LangManager lang) {
        this.waypointManager = waypointManager;
        this.teleportManager = teleportManager;
        this.config = config;
        this.lang = lang;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBeaconBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.BEACON) return;

        Waypoint waypoint = waypointManager.getAtLocation(block.getLocation());
        if (waypoint == null) return;

        Player player = event.getPlayer();
        boolean owner = waypoint.getOwner().equals(player.getUniqueId());
        boolean admin = player.hasPermission("beaconwaypoints.admin");
        if (!admin && !(owner && config.isAllowBeaconBreakByOwner())) {
            event.setCancelled(true);
            player.sendMessage(lang.getMessage("command.waypoint.no-permission"));
            return;
        }

        try {
            if (waypointManager.deleteWaypoint(waypoint.getId())) {
                player.sendMessage(lang.getMessage("beacon.broken",
                    Map.of("name", waypoint.getName())));
            }
        } catch (SQLException exception) {
            logDatabaseError("Failed to delete waypoint on beacon break", exception);
            event.setCancelled(true);
            player.sendMessage(lang.getMessage("error.database"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBeaconInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BEACON
                || !WaypointManager.isActivatedBeacon(block)) return;

        Waypoint waypoint = waypointManager.getAtLocation(block.getLocation());
        if (waypoint == null) return;

        Player player = event.getPlayer();
        discover(player, waypoint);

        if (player.isSneaking()) return;

        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        new BeaconMainMenu(lang, config, waypointManager, teleportManager, player, waypoint)
            .open(player);
    }

    private void discover(Player player, Waypoint waypoint) {
        if (!config.isDiscoveryMode()) return;
        try {
            if (!waypointManager.hasDiscovered(waypoint.getId(), player.getUniqueId())
                    && waypointManager.discoverWaypoint(waypoint.getId(), player.getUniqueId())) {
                player.sendMessage(lang.getMessage("beacon.discovered",
                    Map.of("name", waypoint.getName())));
            }
        } catch (SQLException exception) {
            BeaconWaypoints.getInstance().getLogger().log(Level.WARNING,
                "Failed to record waypoint discovery", exception);
        }
    }

    private void logDatabaseError(String message, SQLException exception) {
        BeaconWaypoints.getInstance().getLogger().log(Level.SEVERE, message, exception);
    }
}
