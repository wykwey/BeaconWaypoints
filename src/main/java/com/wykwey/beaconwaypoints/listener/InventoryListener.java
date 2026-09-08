package com.wykwey.beaconwaypoints.listener;

import com.wykwey.beaconwaypoints.gui.Menu.MenuHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/** Routes Paper inventory events through the custom InventoryHolder. */
public final class InventoryListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof MenuHolder holder)) return;

        event.setCancelled(true);
        if (event.getClickedInventory() != top) return;
        if (event.getClick() != org.bukkit.event.inventory.ClickType.LEFT) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < top.getSize()) {
            holder.getMenu().handleClick(player, rawSlot, event.getClick());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof MenuHolder)) return;

        if (event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize())) {
            event.setCancelled(true);
        }
    }
}
