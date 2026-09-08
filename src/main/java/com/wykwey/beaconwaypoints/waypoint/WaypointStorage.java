package com.wykwey.beaconwaypoints.waypoint;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SQLite 存储层
 */
public class WaypointStorage {
    
    private final JavaPlugin plugin;
    private final String databasePath;

    public WaypointStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.databasePath = new File(plugin.getDataFolder(), "data.db").getPath();
    }

    /**
     * 初始化数据库
     */
    public void initialize() throws SQLException {
        plugin.getDataFolder().mkdirs();
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found", e);
        }

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS waypoints (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    owner TEXT NOT NULL,
                    is_public INTEGER NOT NULL DEFAULT 1,
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    icon TEXT NOT NULL DEFAULT 'BEACON',
                    created_at INTEGER NOT NULL,
                    UNIQUE(world, x, y, z)
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS waypoint_access (
                    waypoint_id INTEGER NOT NULL,
                    player_uuid TEXT NOT NULL,
                    PRIMARY KEY (waypoint_id, player_uuid),
                    FOREIGN KEY (waypoint_id) REFERENCES waypoints(id) ON DELETE CASCADE
                )
            """);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
    }

    /**
     * 插入路径点
     */
    public Waypoint insert(String name, UUID owner, boolean isPublic, Location loc, Material icon) throws SQLException {
        long createdAt = System.currentTimeMillis();
        String sql = "INSERT INTO waypoints (name, owner, is_public, world, x, y, z, icon, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, owner.toString());
            ps.setInt(3, isPublic ? 1 : 0);
            ps.setString(4, loc.getWorld().getName());
            ps.setInt(5, loc.getBlockX());
            ps.setInt(6, loc.getBlockY());
            ps.setInt(7, loc.getBlockZ());
            ps.setString(8, icon.name());
            ps.setLong(9, createdAt);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return new Waypoint(rs.getInt(1), name, owner, isPublic, loc,
                        loc.getWorld().getName(), icon, createdAt);
                }
            }
        }
        throw new SQLException("Failed to insert waypoint");
    }

    /**
     * 删除路径点
     */
    public boolean delete(int id) throws SQLException {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement("DELETE FROM waypoints WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Get every waypoint so the manager can keep private waypoints available
     * independently of player online state.
     */
    public List<Waypoint> getAll() throws SQLException {
        List<Waypoint> list = new ArrayList<>();
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM waypoints ORDER BY created_at DESC")) {
            while (rs.next()) list.add(fromResultSet(rs));
        }
        return list;
    }

    /**
     * 获取所有公共路径点
     */
    public List<Waypoint> getPublic() throws SQLException {
        List<Waypoint> list = new ArrayList<>();
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM waypoints WHERE is_public = 1 ORDER BY created_at DESC")) {
            while (rs.next()) list.add(fromResultSet(rs));
        }
        return list;
    }

    /**
     * 获取玩家的私有路径点
     */
    public List<Waypoint> getPrivate(UUID owner) throws SQLException {
        List<Waypoint> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT * FROM waypoints WHERE owner = ? AND is_public = 0 ORDER BY created_at DESC")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(fromResultSet(rs));
            }
        }
        return list;
    }

    /**
     * 按坐标获取路径点
     */
    public Waypoint getAt(Location loc) throws SQLException {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT * FROM waypoints WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
            ps.setString(1, loc.getWorld().getName());
            ps.setInt(2, loc.getBlockX());
            ps.setInt(3, loc.getBlockY());
            ps.setInt(4, loc.getBlockZ());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return fromResultSet(rs);
            }
        }
        return null;
    }

    /**
     * 更新图标
     */
    public boolean updateIcon(int id, Material icon) throws SQLException {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement("UPDATE waypoints SET icon = ? WHERE id = ?")) {
            ps.setString(1, icon.name());
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 授予访问权限
     */
    public boolean grantAccess(int waypointId, UUID player) throws SQLException {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO waypoint_access (waypoint_id, player_uuid) VALUES (?, ?)")) {
            ps.setInt(1, waypointId);
            ps.setString(2, player.toString());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 撤销访问权限
     */
    public boolean revokeAccess(int waypointId, UUID player) throws SQLException {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement("DELETE FROM waypoint_access WHERE waypoint_id = ? AND player_uuid = ?")) {
            ps.setInt(1, waypointId);
            ps.setString(2, player.toString());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 检查是否有访问权限
     */
    public boolean hasAccess(int waypointId, UUID player) throws SQLException {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM waypoint_access WHERE waypoint_id = ? AND player_uuid = ?")) {
            ps.setInt(1, waypointId);
            ps.setString(2, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private Waypoint fromResultSet(ResultSet rs) throws SQLException {
        String worldName = rs.getString("world");
        org.bukkit.World world = plugin.getServer().getWorld(worldName);
        Location location = new Location(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
        Material icon;
        try {
            icon = Material.valueOf(rs.getString("icon"));
        } catch (IllegalArgumentException exception) {
            icon = Material.BEACON;
        }
        return new Waypoint(
            rs.getInt("id"),
            rs.getString("name"),
            UUID.fromString(rs.getString("owner")),
            rs.getInt("is_public") == 1,
            location,
            worldName,
            icon,
            rs.getLong("created_at")
        );
    }
}
