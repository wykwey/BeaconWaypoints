package com.wykwey.beaconwaypoints.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.wykwey.beaconwaypoints.config.LangManager;
import com.wykwey.beaconwaypoints.config.PluginConfig;
import com.wykwey.beaconwaypoints.waypoint.WaypointManager;
import com.wykwey.beaconwaypoints.waypoint.WaypointManager.RegisterResult;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/** Paper 26.2 Brigadier command for registering and reloading waypoints. */
public final class WaypointCommand {

    private static final String USE_PERMISSION = "beaconwaypoints.use";
    private static final String ADMIN_PERMISSION = "beaconwaypoints.admin";

    private final WaypointManager waypointManager;
    private final PluginConfig config;
    private final LangManager lang;

    public WaypointCommand(WaypointManager waypointManager, PluginConfig config, LangManager lang) {
        this.waypointManager = waypointManager;
        this.config = config;
        this.lang = lang;
    }

    public LiteralCommandNode<CommandSourceStack> createCommand() {
        return Commands.literal("waypoint")
            .requires(source -> hasPermission(source.getSender(), USE_PERMISSION))
            .then(registerBranch())
            .then(reloadBranch())
            .build();
    }

    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> registerBranch() {
        return Commands.argument("name", StringArgumentType.word())
            .executes(context -> executeRegister(
                context.getSource().getSender(),
                StringArgumentType.getString(context, "name"), true))
            .then(Commands.literal("public")
                .executes(context -> executeRegister(
                    context.getSource().getSender(),
                    StringArgumentType.getString(context, "name"), true)))
            .then(Commands.literal("private")
                .executes(context -> executeRegister(
                    context.getSource().getSender(),
                    StringArgumentType.getString(context, "name"), false)));
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> reloadBranch() {
        return Commands.literal("reload")
            .requires(source -> hasPermission(source.getSender(), ADMIN_PERMISSION))
            .executes(context -> {
                config.reloadConfig();
                lang.reloadLanguages();
                waypointManager.reloadCache();
                context.getSource().getSender().sendMessage(lang.getMessage("command.waypoint.reloaded"));
                return Command.SINGLE_SUCCESS;
            });
    }

    private int executeRegister(CommandSender sender, String name, boolean publicWaypoint) {
        if (!(sender instanceof Player player)) return 0;

        Location base = player.getLocation().clone().subtract(0, 1, 0);
        RegisterResult result = waypointManager.registerPlayerWaypoint(
            name, player.getUniqueId(), publicWaypoint, base);
        player.sendMessage(lang.getMessage(
            result.messageKey(), placeholders(name, publicWaypoint, result)));
        return result == RegisterResult.OK ? Command.SINGLE_SUCCESS : 0;
    }

    private Map<String, String> placeholders(String name, boolean publicWaypoint, RegisterResult result) {
        return switch (result) {
            case OK, DUPLICATE_NAME -> Map.of("name", name);
            case NAME_TOO_LONG -> Map.of("max", String.valueOf(config.getMaxNameLength()));
            case LIMIT_REACHED -> Map.of(
                "type", lang.getMessage(publicWaypoint ? "misc.public" : "misc.private"),
                "max", String.valueOf(publicWaypoint
                    ? config.getMaxPublicWaypoints() : config.getMaxPrivateWaypoints()));
            default -> Map.of();
        };
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        return sender.hasPermission(permission);
    }
}
