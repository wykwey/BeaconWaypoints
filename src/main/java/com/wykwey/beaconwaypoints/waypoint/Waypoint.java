package com.wykwey.beaconwaypoints.waypoint;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.Objects;
import java.util.UUID;

/** Immutable waypoint domain object. */
public final class Waypoint {

    private final int id;
    private final String name;
    private final UUID owner;
    private final boolean publicWaypoint;
    private final Location location;
    private final String worldName;
    private final Material icon;
    private final long createdAt;

    public Waypoint(int id, String name, UUID owner, boolean publicWaypoint,
                    Location location, Material icon, long createdAt) {
        this(id, name, owner, publicWaypoint, location,
            location.getWorld() == null ? "unknown" : location.getWorld().getName(), icon, createdAt);
    }

    public Waypoint(int id, String name, UUID owner, boolean publicWaypoint,
                    Location location, String worldName, Material icon, long createdAt) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.publicWaypoint = publicWaypoint;
        this.location = Objects.requireNonNull(location, "location").toBlockLocation();
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public UUID getOwner() { return owner; }
    public boolean isPublic() { return publicWaypoint; }
    public Location getLocation() { return location.clone(); }
    public String getWorldName() { return worldName; }
    public String getCoordKey() { return worldName + ":" + getX() + ":" + getY() + ":" + getZ(); }
    public int getX() { return location.getBlockX(); }
    public int getY() { return location.getBlockY(); }
    public int getZ() { return location.getBlockZ(); }
    public Material getIcon() { return icon; }
    public long getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object other) {
        return other instanceof Waypoint waypoint && id == waypoint.id;
    }

    @Override
    public int hashCode() { return Integer.hashCode(id); }

    @Override
    public String toString() {
        return "Waypoint{id=" + id + ", name='" + name + "', owner=" + owner
            + ", public=" + publicWaypoint + ", location=" + getCoordKey() + '}';
    }
}
