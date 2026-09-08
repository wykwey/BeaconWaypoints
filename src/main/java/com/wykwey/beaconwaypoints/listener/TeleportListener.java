package com.wykwey.beaconwaypoints.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks active waypoint flights and suppresses the landing fall damage. */
public final class TeleportListener implements Listener {

    private final Set<UUID> teleporting = ConcurrentHashMap.newKeySet();

    public boolean markTeleporting(Player player) {
        return teleporting.add(player.getUniqueId());
    }

    public void unmarkTeleporting(Player player) {
        teleporting.remove(player.getUniqueId());
    }

    public boolean isTeleporting(Player player) {
        return teleporting.contains(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && teleporting.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        teleporting.remove(event.getPlayer().getUniqueId());
    }
}
