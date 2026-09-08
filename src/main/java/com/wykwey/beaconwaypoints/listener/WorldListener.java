package com.wykwey.beaconwaypoints.listener;

import com.wykwey.beaconwaypoints.waypoint.WaypointManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

/** Re-indexes waypoint locations when Paper loads their world. */
public final class WorldListener implements Listener {

    private final WaypointManager waypointManager;

    public WorldListener(WaypointManager waypointManager) {
        this.waypointManager = waypointManager;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        waypointManager.onWorldLoaded(event.getWorld());
    }
}
