package com.wykwey.beaconwaypoints;

import com.wykwey.beaconwaypoints.command.WaypointCommand;
import com.wykwey.beaconwaypoints.config.LangManager;
import com.wykwey.beaconwaypoints.config.PluginConfig;
import com.wykwey.beaconwaypoints.listener.BeaconListener;
import com.wykwey.beaconwaypoints.listener.InventoryListener;
import com.wykwey.beaconwaypoints.listener.TeleportListener;
import com.wykwey.beaconwaypoints.listener.WorldListener;
import com.wykwey.beaconwaypoints.waypoint.TeleportManager;
import com.wykwey.beaconwaypoints.waypoint.WaypointManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * BeaconWaypoints - 信标路径点系统
 */
public class BeaconWaypoints extends JavaPlugin {

    private static BeaconWaypoints instance;
    private PluginConfig config;
    private LangManager lang;
    private WaypointManager waypointManager;
    private TeleportManager teleportManager;

    @Override
    public void onEnable() {
        instance = this;
        config = new PluginConfig(this);
        config.loadConfig();
        
        lang = new LangManager(this);
        lang.loadLanguages();
        
        waypointManager = new WaypointManager(this, config);
        waypointManager.initialize();
        
        TeleportListener teleportListener = new TeleportListener();
        teleportManager = new TeleportManager(this, config, lang, teleportListener, waypointManager);
        
        getServer().getPluginManager().registerEvents(new BeaconListener(waypointManager, teleportManager, config, lang), this);
        getServer().getPluginManager().registerEvents(new InventoryListener(), this);
        getServer().getPluginManager().registerEvents(new WorldListener(waypointManager), this);
        getServer().getPluginManager().registerEvents(teleportListener, this);
        
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(
                new WaypointCommand(waypointManager, config, lang).createCommand(),
                "信标路径点命令",
                List.of("wp", "waypoints")
            );
        });

        getLogger().info("BeaconWaypoints 已启用！");
    }

    @Override
    public void onDisable() {
        if (teleportManager != null) {
            teleportManager.shutdown();
        }
        if (waypointManager != null) {
            waypointManager.shutdown();
        }
        getLogger().info("BeaconWaypoints 已禁用！");
    }

    public static BeaconWaypoints getInstance() {
        return instance;
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public WaypointManager getWaypointManager() {
        return waypointManager;
    }
}
