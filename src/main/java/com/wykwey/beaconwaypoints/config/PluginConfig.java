package com.wykwey.beaconwaypoints.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 插件配置管理
 */
public class PluginConfig {
    
    private final JavaPlugin plugin;
    private FileConfiguration config;

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 加载配置
     */
    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        validateConfig();
    }

    /**
     * 验证配置
     */
    private void validateConfig() {
        // A list menu needs one content row and one navigation row (2-5 total).
        int publicRows = config.getInt("public-waypoint-menu-rows");
        if (publicRows < 2 || publicRows > 5) {
            plugin.getLogger().warning("Invalid public-waypoint-menu-rows: " + publicRows);
            config.set("public-waypoint-menu-rows", 3);
        }
        
        int privateRows = config.getInt("private-waypoint-menu-rows");
        if (privateRows < 2 || privateRows > 5) {
            plugin.getLogger().warning("Invalid private-waypoint-menu-rows: " + privateRows);
            config.set("private-waypoint-menu-rows", 2);
        }
        
        // 验证图标列表
        if (config.getStringList("waypoint-icons").isEmpty()) {
            plugin.getLogger().warning("No waypoint icons configured, using defaults");
            config.set("waypoint-icons", Arrays.asList(
                "BEACON", "ENDER_PEARL", "ENDER_EYE", "COMPASS", "CLOCK"
            ));
        }
    }

    /**
     * 重载配置
     */
    public void reloadConfig() {
        loadConfig();
    }

    // ==================== 配置读取 ====================

    public int getMaxPublicWaypoints() {
        return config.getInt("max-public-waypoints");
    }

    public int getMaxPrivateWaypoints() {
        return config.getInt("max-private-waypoints");
    }

    public int getLaunchPlayerHeight() {
        return config.getInt("launch-player-height", 576);
    }

    public boolean isDisableGroupTeleporting() {
        return config.getBoolean("disable-group-teleporting");
    }

    public boolean isAllowBeaconBreakByOwner() {
        return config.getBoolean("allow-beacon-break-by-owner");
    }

    public boolean isDiscoveryMode() {
        return config.getBoolean("discovery-mode");
    }

    /**
     * 检查世界是否允许
     */
    public boolean isWorldAllowed(String worldName) {
        if (config.getBoolean("allow-all-worlds")) return true;
        return config.getStringList("allowed-worlds").contains(worldName);
    }

    /**
     * 公共路径点菜单行数（1-5）
     */
    public int getPublicMenuRows() {
        return Math.max(2, Math.min(5, config.getInt("public-waypoint-menu-rows", 3)));
    }

    /**
     * 私有路径点菜单行数（1-5）
     */
    public int getPrivateMenuRows() {
        return Math.max(2, Math.min(5, config.getInt("private-waypoint-menu-rows", 2)));
    }

    // ==================== 名称校验（长度与字符分开判断，便于细分提示） ====================

    /**
     * 获取名称最大长度
     */
    public int getMaxNameLength() {
        return config.getInt("max-name-length", 32);
    }

    /**
     * 是否强制字母数字命名
     */
    public boolean isForceAlphanumericNames() {
        return config.getBoolean("force-alphanumeric-names");
    }

    /**
     * 名称字符是否有效（长度由 getMaxNameLength 单独校验）
     */
    public boolean isValidWaypointName(String name) {
        if (name == null || name.isEmpty()) return false;
        if (isForceAlphanumericNames()) {
            return name.matches("^[a-zA-Z0-9_\\-]+$");
        }
        return true;
    }

    /**
     * 获取可用的路径点图标（过滤无效 Material）
     */
    public List<Material> getWaypointIcons() {
        List<Material> icons = new ArrayList<>();
        for (String s : config.getStringList("waypoint-icons")) {
            try {
                icons.add(Material.valueOf(s.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Unknown waypoint icon: " + s);
            }
        }
        return icons;
    }
}
