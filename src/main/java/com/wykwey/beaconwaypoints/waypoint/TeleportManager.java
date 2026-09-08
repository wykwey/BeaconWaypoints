package com.wykwey.beaconwaypoints.waypoint;

import com.wykwey.beaconwaypoints.config.LangManager;
import com.wykwey.beaconwaypoints.config.PluginConfig;
import com.wykwey.beaconwaypoints.listener.TeleportListener;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Coordinates the complete beacon flight lifecycle. */
public final class TeleportManager {

    private static final long WARMUP_TICKS = 50;
    private static final long CHECK_PERIOD = 5;
    private static final int MAX_CHECKS = 80;
    private static final double BEAM_RADIUS = 0.125;
    private static final double GROUP_RADIUS = 5.0;

    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final LangManager lang;
    private final TeleportListener teleportListener;
    private final WaypointManager waypointManager;
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public TeleportManager(JavaPlugin plugin, PluginConfig config, LangManager lang,
                           TeleportListener teleportListener, WaypointManager waypointManager) {
        this.plugin = plugin;
        this.config = config;
        this.lang = lang;
        this.teleportListener = teleportListener;
        this.waypointManager = waypointManager;
    }

    public void shutdown() {
        pending.clear();
    }

    public void teleport(Player clicker, Waypoint start, Waypoint destination) {
        if (clicker == null || !clicker.isOnline() || destination == null) return;
        UUID clickerId = clicker.getUniqueId();
        if (!pending.add(clickerId) || teleportListener.isTeleporting(clicker)) return;

        Location launch = launchLocation(clicker, start);
        Location target = destination.getLocation();
        if (launch.getWorld() == null || target.getWorld() == null) {
            pending.remove(clickerId);
            return;
        }

        clicker.sendMessage(lang.getMessage("teleport.starting", Map.of("name", destination.getName())));
        World launchWorld = launch.getWorld();
        launchWorld.spawnParticle(Particle.PORTAL, launch, 500, 1, 1, 1);
        launchWorld.playSound(launch, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 1, 1);
        launchWorld.playSound(launch, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1, 1);

        new BukkitRunnable() {
            @Override
            public void run() {
                launchWorld.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, launch, 50);
                launchWorld.spawnParticle(Particle.FIREWORK, launch, 500, 1, 1, 1);
                launchWorld.playSound(launch, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST,
                    SoundCategory.BLOCKS, 2, 1);
                launchWorld.playSound(launch, Sound.BLOCK_BEACON_DEACTIVATE,
                    SoundCategory.BLOCKS, 1, 0.65f);

                if (!clicker.isOnline()) return;

                List<Player> riders = findRiders(launch, clicker);
                if (riders.isEmpty()) return;

                FlightContext context = new FlightContext(
                    destination,
                    launchWorld,
                    launch.getX(),
                    launch.getZ(),
                    launch.getBlockY(),
                    beamTop(launchWorld),
                    target.getWorld(),
                    target.getBlockX() + 0.5,
                    target.getBlockZ() + 0.5,
                    target.getBlockY(),
                    beamTop(target.getWorld()),
                    isReachable(destination)
                );
                for (Player rider : riders) startFlight(rider, clicker, context);
            }
        }.runTaskLater(plugin, WARMUP_TICKS);
    }

    private Location launchLocation(Player player, Waypoint start) {
        if (start != null) return start.getLocation().add(0.5, 1, 0.5);
        Location location = player.getLocation();
        return new Location(location.getWorld(), location.getBlockX() + 0.5,
            location.getBlockY(), location.getBlockZ() + 0.5);
    }

    private List<Player> findRiders(Location launch, Player clicker) {
        int beaconX = launch.getBlockX();
        int beaconY = launch.getBlockY() - 1;
        int beaconZ = launch.getBlockZ();

        if (!isStandingOnBeacon(clicker, launch.getWorld(), beaconX, beaconY, beaconZ)) {
            return List.of();
        }
        if (config.isDisableGroupTeleporting()) {
            return List.of(clicker);
        }

        List<Player> riders = new ArrayList<>();
        for (Entity entity : launch.getWorld().getNearbyEntities(
                launch, GROUP_RADIUS, GROUP_RADIUS, GROUP_RADIUS)) {
            if (entity instanceof Player player && !riders.contains(player)) {
                riders.add(player);
            }
        }
        if (!riders.contains(clicker)) riders.add(clicker);
        return riders;
    }

    private boolean isStandingOnBeacon(Player player, World world,
                                       int beaconX, int beaconY, int beaconZ) {
        Location location = player.getLocation();
        if (!world.equals(location.getWorld())
                || location.getBlockX() != beaconX
                || location.getBlockY() != beaconY + 1
                || location.getBlockZ() != beaconZ) return false;
        return world.getBlockAt(beaconX, beaconY, beaconZ).getType() == Material.BEACON;
    }

    private int beamTop(World world) {
        return Math.max(config.getLaunchPlayerHeight(), world.getMaxHeight());
    }

    private void startFlight(Player player, Player clicker, FlightContext context) {
        if (!teleportListener.markTeleporting(player)) return;
        if (!player.getUniqueId().equals(clicker.getUniqueId())) {
            player.sendMessage(lang.getMessage("teleport.starting",
                Map.of("name", context.destination().getName())));
        }

        List<LivingEntity> leashed = config.isDisableGroupTeleporting()
            ? List.of() : collectLeashedEntities(player);
        new BukkitRunnable() {
            private int checks;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    finish(player);
                    cancel();
                    return;
                }
                applyFlightEffects(player);
                Location location = player.getLocation();
                if (location.getY() < context.launchTop()) {
                    keepInBeam(player, location, context.launchWorld(),
                        context.launchX(), context.launchZ(), 5);
                    if (++checks >= MAX_CHECKS) {
                        finish(player);
                        cancel();
                    }
                    return;
                }

                cancel();
                descend(player, context, leashed, location);
            }
        }.runTaskTimer(plugin, 0, CHECK_PERIOD);
    }

    private void descend(Player player, FlightContext context, List<LivingEntity> leashed,
                         Location current) {
        boolean returning = !context.destinationActive();
        World world;
        double x;
        double z;
        int floor;

        if (!returning) {
            world = context.destinationWorld();
            x = context.destinationX();
            z = context.destinationZ();
            floor = context.destinationY();
            player.teleport(new Location(world, x, context.destinationTop(), z,
                current.getYaw(), current.getPitch()));
            Location landing = new Location(world, x, floor + 1, z);
            for (LivingEntity entity : leashed) {
                if (entity.isValid()) entity.teleport(landing);
            }
        } else {
            world = context.launchWorld();
            x = context.launchX();
            z = context.launchZ();
            floor = context.launchY() - 1;
            player.sendMessage(lang.getMessage("teleport.destination-not-active",
                Map.of("name", context.destination().getName())));
            removeInactiveWaypoint(context.destination());
        }

        player.removePotionEffect(PotionEffectType.LEVITATION);
        player.setVelocity(new Vector(0, -2, 0));
        startDescent(player, context, world, x, z, floor, returning);
    }

    private void startDescent(Player player, FlightContext context, World world,
                              double x, double z, int floor, boolean returning) {
        new BukkitRunnable() {
            private int checks;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    finish(player);
                    cancel();
                    return;
                }
                Location location = player.getLocation();
                if (location.getY() > floor + 1) {
                    keepInBeam(player, location, world, x, z, -2);
                    if (++checks >= MAX_CHECKS) {
                        finish(player);
                        cancel();
                    }
                    return;
                }

                cancel();
                Location landing = new Location(world, x, floor + 1, z);
                player.teleport(landing);
                if (!returning) {
                    player.sendMessage(lang.getMessage("teleport.success",
                        Map.of("name", context.destination().getName())));
                }
                finish(player);
            }
        }.runTaskTimer(plugin, 0, CHECK_PERIOD);
    }

    private void keepInBeam(Player player, Location location, World world,
                            double x, double z, double velocityY) {
        if (Math.abs(location.getX() - x) > BEAM_RADIUS
                || Math.abs(location.getZ() - z) > BEAM_RADIUS) {
            player.teleport(new Location(world, x, location.getY(), z,
                location.getYaw(), location.getPitch()));
            player.setVelocity(new Vector(0, velocityY, 0));
        }
    }

    private void applyFlightEffects(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION,
            600, 127, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
            600, 255, false, false, false));
    }

    private boolean isReachable(Waypoint waypoint) {
        Location location = waypoint.getLocation();
        if (location.getWorld() == null) return false;
        Block block = location.getBlock();
        return block.getType() == Material.BEACON && WaypointManager.isActivatedBeacon(block);
    }

    private void removeInactiveWaypoint(Waypoint waypoint) {
        try {
            waypointManager.deleteWaypoint(waypoint.getId());
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE,
                "Failed to remove inactive waypoint " + waypoint.getName(), exception);
        }
    }

    private void finish(Player player) {
        teleportListener.unmarkTeleporting(player);
    }

    private List<LivingEntity> collectLeashedEntities(Player player) {
        List<LivingEntity> entities = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
            if (entity instanceof LivingEntity living && living.isLeashed()
                    && player.equals(living.getLeashHolder())) entities.add(living);
        }
        return entities;
    }

    private record FlightContext(Waypoint destination, World launchWorld,
                                 double launchX, double launchZ, int launchY,
                                 int launchTop, World destinationWorld,
                                 double destinationX, double destinationZ,
                                 int destinationY, int destinationTop,
                                 boolean destinationActive) { }
}
