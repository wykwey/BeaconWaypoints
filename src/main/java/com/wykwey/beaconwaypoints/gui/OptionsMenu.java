package com.wykwey.beaconwaypoints.gui;

import com.wykwey.beaconwaypoints.BeaconWaypoints;
import com.wykwey.beaconwaypoints.config.LangManager;
import com.wykwey.beaconwaypoints.config.PluginConfig;
import com.wykwey.beaconwaypoints.waypoint.Waypoint;
import com.wykwey.beaconwaypoints.waypoint.WaypointManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;

/** Waypoint management menu with icon selection and delete confirmation. */
public final class OptionsMenu extends Menu {

    private final PluginConfig config;
    private final WaypointManager waypointManager;
    private final Waypoint waypoint;
    private final Menu parent;
    private boolean confirmingDelete;

    public OptionsMenu(LangManager lang, PluginConfig config, WaypointManager waypointManager,
                       Waypoint waypoint, Player player, Menu parent) {
        super(3, lang.getMessage("gui.title.options"), lang);
        this.config = config;
        this.waypointManager = waypointManager;
        this.waypoint = waypoint;
        this.parent = parent;
    }

    @Override
    protected void updateButtons() {
        clearButtons();

        setButton(11, new Button(
            Material.PAINTING,
            lang.getMessage("gui.button.change-icon"),
            null,
            (player, click) -> openIconPicker(player)
        ));

        setButton(15, new Button(
            confirmingDelete ? Material.RED_WOOL : Material.BARRIER,
            lang.getMessage(confirmingDelete ? "gui.button.delete-confirm" : "gui.button.delete"),
            null,
            (player, click) -> {
                if (confirmingDelete) deleteWaypoint(player);
                else {
                    confirmingDelete = true;
                    refresh(player);
                }
            }
        ));

        addBackButton((player, click) -> {
            if (parent == null) player.closeInventory();
            else parent.open(player);
        });
    }

    private void openIconPicker(Player player) {
        new IconPickerMenu(
            lang,
            config.getWaypointIcons(),
            icon -> updateIcon(player, icon),
            () -> open(player)
        ).open(player);
    }

    private void updateIcon(Player player, Material icon) {
        try {
            if (waypointManager.updateIcon(waypoint.getId(), icon)) {
                player.sendMessage(lang.getMessage("gui.message.icon-updated", Map.of("icon", icon.name())));
            }
        } catch (SQLException exception) {
            logDatabaseError("Failed to update icon", exception);
            player.sendMessage(lang.getMessage("error.database"));
        }
    }

    private void deleteWaypoint(Player player) {
        try {
            if (waypointManager.deleteWaypoint(waypoint.getId())) {
                player.sendMessage(lang.getMessage("command.waypoint.deleted",
                    Map.of("name", waypoint.getName())));
            }
        } catch (SQLException exception) {
            logDatabaseError("Failed to delete waypoint", exception);
            player.sendMessage(lang.getMessage("error.database"));
        } finally {
            player.closeInventory();
        }
    }

    private void logDatabaseError(String message, SQLException exception) {
        BeaconWaypoints.getInstance().getLogger().log(Level.SEVERE, message, exception);
    }
}
