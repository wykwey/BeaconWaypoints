package com.wykwey.beaconwaypoints.gui;

import com.wykwey.beaconwaypoints.config.LangManager;
import com.wykwey.beaconwaypoints.config.PluginConfig;
import com.wykwey.beaconwaypoints.waypoint.TeleportManager;
import com.wykwey.beaconwaypoints.waypoint.Waypoint;
import com.wykwey.beaconwaypoints.waypoint.WaypointManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Root menu opened from a registered beacon. */
public final class BeaconMainMenu extends Menu {

    private final PluginConfig config;
    private final WaypointManager waypointManager;
    private final TeleportManager teleportManager;
    private final Waypoint origin;
    private final Player player;

    public BeaconMainMenu(LangManager lang, PluginConfig config, WaypointManager waypointManager,
                          TeleportManager teleportManager, Player player, Waypoint origin) {
        super(3, lang.getMessage("gui.title.beacon-menu"), lang);
        this.config = config;
        this.waypointManager = waypointManager;
        this.teleportManager = teleportManager;
        this.origin = origin;
        this.player = player;
    }

    @Override
    protected void updateButtons() {
        clearButtons();

        if (origin != null && player.getUniqueId().equals(origin.getOwner())) {
            setButton(11, new Button(
                Material.REPEATER,
                lang.getMessage("gui.button.settings"),
                List.of(lang.getMessage("gui.lore.settings")),
                (target, click) -> new OptionsMenu(
                    lang, config, waypointManager, origin, target, this).open(target)
            ));
        }

        setButton(13, new Button(
            Material.BEACON,
            lang.getMessage("gui.button.public-beacons"),
            List.of(lang.getMessage("gui.lore.public-beacons")),
            (target, click) -> new WaypointListMenu(
                lang, config, waypointManager, teleportManager, origin, target, true).open(target)
        ));

        setButton(15, new Button(
            Material.ENDER_CHEST,
            lang.getMessage("gui.button.private-beacons"),
            List.of(lang.getMessage("gui.lore.private-beacons")),
            (target, click) -> new WaypointListMenu(
                lang, config, waypointManager, teleportManager, origin, target, false).open(target)
        ));
    }
}
