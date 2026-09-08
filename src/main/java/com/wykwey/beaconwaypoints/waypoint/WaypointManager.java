package com.wykwey.beaconwaypoints.waypoint;

import com.wykwey.beaconwaypoints.config.PluginConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Domain service and in-memory index for beacon waypoints. */
public final class WaypointManager {

    public enum RegisterResult {
        OK("command.waypoint.registered"),
        NAME_TOO_LONG("command.waypoint.name-too-long"),
        NAME_INVALID("command.waypoint.name-invalid"),
        WORLD_NOT_ALLOWED("command.waypoint.world-not-allowed"),
        NOT_ON_BEACON("command.waypoint.not-on-beacon"),
        BEACON_NOT_ACTIVE("command.waypoint.beacon-not-active"),
        LIMIT_REACHED("command.waypoint.limit-reached"),
        DUPLICATE_NAME("command.waypoint.duplicate-name"),
        LOCATION_TAKEN("command.waypoint.already-exists"),
        DB_ERROR("error.database");

        private final String messageKey;

        RegisterResult(String messageKey) { this.messageKey = messageKey; }
        public String messageKey() { return messageKey; }
    }

    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final WaypointStorage storage;
    private final Map<Integer, Waypoint> byId = new ConcurrentHashMap<>();
    private final Map<String, Waypoint> byCoordinate = new ConcurrentHashMap<>();

    public WaypointManager(JavaPlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.storage = new WaypointStorage(plugin);
    }

    public void initialize() { reloadCache(); }

    public void reloadCache() {
        byId.clear();
        byCoordinate.clear();
        try {
            storage.initialize();
            for (Waypoint waypoint : storage.getAll()) index(waypoint);
            plugin.getLogger().info("Loaded " + byId.size() + " waypoints.");
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize waypoint storage", exception);
        }
    }

    public void onWorldLoaded(org.bukkit.World world) {
        byId.values().stream()
            .filter(waypoint -> waypoint.getWorldName().equals(world.getName()))
            .forEach(waypoint -> byCoordinate.put(waypoint.getCoordKey(), waypoint));
    }

    private void index(Waypoint waypoint) {
        byId.put(waypoint.getId(), waypoint);
        if (plugin.getServer().getWorld(waypoint.getWorldName()) != null) {
            byCoordinate.put(waypoint.getCoordKey(), waypoint);
        }
    }

    private void unindex(Waypoint waypoint) {
        byId.remove(waypoint.getId());
        byCoordinate.remove(waypoint.getCoordKey());
    }

    private static String coordinateKey(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":"
            + location.getBlockY() + ":" + location.getBlockZ();
    }

    public Waypoint registerWaypoint(String name, UUID owner, boolean publicWaypoint,
                                     Location location, Material icon) throws SQLException {
        if (location.getWorld() == null) throw new SQLException("Waypoint world is missing");
        String key = coordinateKey(location);
        if (byCoordinate.containsKey(key)) throw new SQLException("Already exists");

        Waypoint waypoint = storage.insert(name, owner, publicWaypoint,
            location.toBlockLocation(), icon);
        index(waypoint);
        return waypoint;
    }

    public boolean isNameTakenBy(String name, UUID owner) {
        return byId.values().stream().anyMatch(waypoint ->
            waypoint.getOwner().equals(owner) && waypoint.getName().equalsIgnoreCase(name));
    }

    public RegisterResult registerPlayerWaypoint(String name, UUID owner, boolean publicWaypoint, Location base) {
        if (name == null || name.length() > config.getMaxNameLength()) return RegisterResult.NAME_TOO_LONG;
        if (!config.isValidWaypointName(name)) return RegisterResult.NAME_INVALID;
        if (base == null || base.getWorld() == null
                || !config.isWorldAllowed(base.getWorld().getName())) {
            return RegisterResult.WORLD_NOT_ALLOWED;
        }

        Block block = base.getBlock();
        if (block.getType() != Material.BEACON) return RegisterResult.NOT_ON_BEACON;
        if (!isActivatedBeacon(block)) return RegisterResult.BEACON_NOT_ACTIVE;

        int current = publicWaypoint ? getPublicWaypoints().size() : getPrivateWaypoints(owner).size();
        int maximum = publicWaypoint ? config.getMaxPublicWaypoints() : config.getMaxPrivateWaypoints();
        if (current >= maximum) return RegisterResult.LIMIT_REACHED;
        if (isNameTakenBy(name, owner)) return RegisterResult.DUPLICATE_NAME;

        try {
            registerWaypoint(name, owner, publicWaypoint, base, Material.BEACON);
            return RegisterResult.OK;
        } catch (SQLException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("Already exists")) {
                return RegisterResult.LOCATION_TAKEN;
            }
            plugin.getLogger().log(Level.SEVERE, "Failed to register waypoint", exception);
            return RegisterResult.DB_ERROR;
        }
    }

    public static boolean isActivatedBeacon(Block block) {
        return block.getState() instanceof Beacon beacon && beacon.getTier() > 0;
    }

    public boolean deleteWaypoint(int id) throws SQLException {
        Waypoint waypoint = byId.get(id);
        return waypoint != null && storage.delete(id) && removeAfterDelete(waypoint);
    }

    private boolean removeAfterDelete(Waypoint waypoint) {
        unindex(waypoint);
        return true;
    }

    public Waypoint getById(int id) { return byId.get(id); }

    public Waypoint getAtLocation(Location location) {
        if (location == null || location.getWorld() == null) return null;
        return byCoordinate.get(coordinateKey(location));
    }

    public List<Waypoint> getPublicWaypoints() {
        return byId.values().stream()
            .filter(Waypoint::isPublic)
            .sorted(Comparator.comparingLong(Waypoint::getCreatedAt).reversed())
            .toList();
    }

    public List<Waypoint> getPrivateWaypoints(UUID owner) {
        return byId.values().stream()
            .filter(waypoint -> !waypoint.isPublic() && waypoint.getOwner().equals(owner))
            .sorted(Comparator.comparingLong(Waypoint::getCreatedAt).reversed())
            .toList();
    }

    public boolean updateIcon(int id, Material icon) throws SQLException {
        if (icon == null || !storage.updateIcon(id, icon)) return false;
        Waypoint old = byId.get(id);
        if (old != null) {
            index(new Waypoint(id, old.getName(), old.getOwner(), old.isPublic(),
                old.getLocation(), old.getWorldName(), icon, old.getCreatedAt()));
        }
        return true;
    }

    public boolean hasAccess(int waypointId, UUID player) throws SQLException {
        return storage.hasAccess(waypointId, player);
    }

    public boolean hasDiscovered(int waypointId, UUID player) throws SQLException {
        return hasAccess(waypointId, player);
    }

    public boolean discoverWaypoint(int waypointId, UUID player) throws SQLException {
        return storage.grantAccess(waypointId, player);
    }

    public void shutdown() {
        byId.clear();
        byCoordinate.clear();
    }
}
