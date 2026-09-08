package com.wykwey.beaconwaypoints.gui;

import com.wykwey.beaconwaypoints.BeaconWaypoints;
import com.wykwey.beaconwaypoints.config.LangManager;
import com.wykwey.beaconwaypoints.config.PluginConfig;
import com.wykwey.beaconwaypoints.waypoint.TeleportManager;
import com.wykwey.beaconwaypoints.waypoint.Waypoint;
import com.wykwey.beaconwaypoints.waypoint.WaypointManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Paginated public or private waypoint list. */
public final class WaypointListMenu extends Menu {

    private final WaypointManager waypointManager;
    private final TeleportManager teleportManager;
    private final PluginConfig config;
    private final Waypoint origin;
    private final Player player;
    private final boolean publicList;

    public WaypointListMenu(LangManager lang, PluginConfig config, WaypointManager waypointManager,
                            TeleportManager teleportManager, Waypoint origin, Player player,
                            boolean publicList) {
        super(publicList ? config.getPublicMenuRows() : config.getPrivateMenuRows(),
            lang.getMessage(publicList ? "gui.title.public-waypoints" : "gui.title.private-waypoints"), lang);
        this.waypointManager = waypointManager;
        this.teleportManager = teleportManager;
        this.config = config;
        this.origin = origin;
        this.player = player;
        this.publicList = publicList;
    }

    @Override
    protected void updateButtons() {
        clearButtons();

        List<Waypoint> waypoints = loadWaypoints();
        addNavigation(waypoints.size());

        int start = page * getContentSize();
        int end = Math.min(start + getContentSize(), waypoints.size());
        for (int index = start; index < end; index++) {
            Waypoint waypoint = waypoints.get(index);
            setButton(index - start, waypointButton(waypoint));
        }

        addBackButton((player, click) -> new BeaconMainMenu(
            lang, config, waypointManager, teleportManager, player, origin).open(player));
    }

    private List<Waypoint> loadWaypoints() {
        List<Waypoint> waypoints = publicList
            ? waypointManager.getPublicWaypoints()
            : waypointManager.getPrivateWaypoints(player.getUniqueId());

        if (!publicList && BeaconWaypoints.getInstance().getConfig().getBoolean("discovery-mode")) {
            waypoints = waypoints.stream()
                .filter(waypoint -> waypoint.getOwner().equals(player.getUniqueId()) || discovered(waypoint))
                .toList();
        }

        if (origin != null) {
            waypoints = waypoints.stream()
                .filter(waypoint -> !waypoint.getCoordKey().equals(origin.getCoordKey()))
                .toList();
        }
        return waypoints;
    }

    private Button waypointButton(Waypoint waypoint) {
        Map<String, String> placeholders = new HashMap<>(Map.of(
            "x", String.valueOf(waypoint.getX()),
            "y", String.valueOf(waypoint.getY()),
            "z", String.valueOf(waypoint.getZ()),
            "world", waypoint.getWorldName(),
            "owner", ownerName(waypoint)
        ));
        return new Button(
            safeIcon(waypoint.getIcon()),
            "§6" + waypoint.getName(),
            lang.getMessageList("gui.lore.waypoint", placeholders),
            (target, click) -> teleport(target, waypoint)
        );
    }

    private Material safeIcon(Material icon) {
        return icon == null ? Material.BEACON : icon;
    }

    private void teleport(Player target, Waypoint destination) {
        target.closeInventory();
        Bukkit.getScheduler().runTask(BeaconWaypoints.getInstance(),
            () -> teleportManager.teleport(target, origin, destination));
    }

    private boolean discovered(Waypoint waypoint) {
        try {
            return waypointManager.hasDiscovered(waypoint.getId(), player.getUniqueId());
        } catch (Exception ignored) {
            return false;
        }
    }

    private String ownerName(Waypoint waypoint) {
        String name = Bukkit.getOfflinePlayer(waypoint.getOwner()).getName();
        return name != null ? name : waypoint.getOwner().toString().substring(0, 8);
    }
}
