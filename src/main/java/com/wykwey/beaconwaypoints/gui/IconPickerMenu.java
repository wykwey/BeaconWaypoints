package com.wykwey.beaconwaypoints.gui;

import com.wykwey.beaconwaypoints.config.LangManager;
import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/** Paginated icon selector used by the waypoint management menu. */
public final class IconPickerMenu extends Menu {

    private final List<Material> icons;
    private final Callback callback;
    private final Runnable onCancel;

    public IconPickerMenu(LangManager lang, List<Material> icons, Callback callback, Runnable onCancel) {
        super(5, lang.getMessage("gui.title.icon-picker"), lang);
        this.icons = List.copyOf(icons);
        this.callback = callback;
        this.onCancel = onCancel;
    }

    public interface Callback {
        void onSelect(Material icon);
    }

    @Override
    protected void updateButtons() {
        clearButtons();
        addNavigation(icons.size());

        int start = page * getContentSize();
        int end = Math.min(start + getContentSize(), icons.size());
        for (int index = start; index < end; index++) {
            Material icon = icons.get(index);
            setButton(index - start, new Button(
                icon,
                "§e" + icon.name(),
                lang.getMessageList("gui.lore.icon", Map.of()),
                (player, click) -> callback.onSelect(icon)
            ));
        }

        addBackButton((player, click) -> {
            if (onCancel == null) player.closeInventory();
            else onCancel.run();
        });
    }
}
