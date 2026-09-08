package com.wykwey.beaconwaypoints.gui;

import com.wykwey.beaconwaypoints.config.LangManager;
import com.wykwey.beaconwaypoints.BeaconWaypoints;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI 菜单基类
 *
 * - 基于 InventoryHolder 路由点击
 * - 支持翻页后原地刷新（修复旧版改 page 不重绘的 bug）
 * - 带页码指示与按钮动作异常保护
 */
public class Menu {

    /** § 遗留色码 → Adventure Component 的统一转换入口 */
    protected static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /**
     * 菜单界面持有者：让事件监听器通过 inventory.getHolder()
     * 精确识别插件菜单（与 Menu 强耦合，合并为嵌套类）。
     */
    public static final class MenuHolder implements InventoryHolder {

        private final Menu menu;
        private Inventory inventory;

        MenuHolder(Menu menu) {
            this.menu = menu;
        }

        void attach(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public Menu getMenu() {
            return menu;
        }
    }

    /** 按钮动作：拿到触发点击的玩家与点击方式 */
    public interface ClickAction {
        void run(Player player, org.bukkit.event.inventory.ClickType clickType);
    }

    /**
     * GUI 按钮
     */
    public static class Button {
        private final ItemStack item;
        private final ClickAction action;

        public Button(Material material, String legacyName, List<String> legacyLore, ClickAction action) {
            this.item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (legacyName != null) meta.displayName(LEGACY.deserialize(legacyName));
                if (legacyLore != null && !legacyLore.isEmpty()) {
                    meta.lore(legacyLore.stream().map(LEGACY::deserialize).toList());
                }
                item.setItemMeta(meta);
            }
            this.action = action;
        }

        public ItemStack getItem() {
            return item;
        }

        void click(Player player, ClickType clickType) {
            if (action != null) action.run(player, clickType);
        }
    }

    protected final int rows;
    protected final String title;
    protected final LangManager lang;
    private final Map<Integer, Button> buttons = new HashMap<>();
    protected int page = 0;
    protected int totalPages = 1;

    public Menu(int rows, String title, LangManager lang) {
        this.rows = Math.max(1, Math.min(5, rows));
        this.title = title;
        this.lang = lang;
    }

    /**
     * 打开菜单
     */
    public final void open(Player player) {
        updateButtons();
        MenuHolder holder = new MenuHolder(this);
        Inventory inventory = BeaconWaypoints.getInstance().getServer()
            .createInventory(holder, rows * 9, titleComponent());
        holder.attach(inventory);
        fill(inventory);
        player.openInventory(inventory);
    }

    /**
     * 原地刷新当前打开的同名界面；若已关闭则重新打开。
     * 翻页、状态切换统一走这里。
     */
    public final void refresh(Player player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder(false) instanceof MenuHolder holder && holder.getMenu() == this) {
            updateButtons();
            top.clear();
            fill(top);
            player.updateInventory();
        } else {
            open(player);
        }
    }

    private Component titleComponent() {
        return LEGACY.deserialize(title);
    }

    private void fill(Inventory inventory) {
        buttons.forEach((slot, button) -> inventory.setItem(slot, button.getItem()));
    }

    protected void updateButtons() {}

    protected final void setButton(int slot, Button button) {
        buttons.put(slot, button);
    }

    protected final void clearButtons() {
        buttons.clear();
    }

    public final void handleClick(Player player, int slot, ClickType clickType) {
        if (clickType != ClickType.LEFT) return;

        Button button = buttons.get(slot);
        if (button == null) return;

        try {
            button.click(player, clickType);
        } catch (RuntimeException exception) {
            plugin().getLogger().severe("Menu button error on " + getClass().getSimpleName() + ": " + exception);
        }
    }

    /**
     * 内容区大小（不含底部导航行）
     */
    protected final int getContentSize() {
        return (rows - 1) * 9;
    }

    /**
     * 底部导航行：上一页 / 页码 / 下一页
     */
    protected final void addNavigation(int totalItems) {
        totalPages = Math.max(1, (int) Math.ceil((double) totalItems / Math.max(1, getContentSize())));
        page = Math.max(0, Math.min(page, totalPages - 1));
        int bottomRow = (rows - 1) * 9;

        // 页码指示
        setButton(bottomRow + 4, new Button(
            Material.GRAY_STAINED_GLASS_PANE,
            lang.getMessage("gui.button.page-info", Map.of(
                "page", String.valueOf(page + 1),
                "total", String.valueOf(totalPages)
            )),
            null,
            null
        ));

        if (page > 0) {
            setButton(bottomRow, new Button(
                Material.ARROW,
                lang.getMessage("gui.button.previous-page"),
                List.of(lang.getMessage("gui.lore.page", Map.of("page", String.valueOf(page)))),
                (player, click) -> {
                    page--;
                    refresh(player);
                }
            ));
        }
        if (page < totalPages - 1) {
            setButton(bottomRow + 8, new Button(
                Material.ARROW,
                lang.getMessage("gui.button.next-page"),
                List.of(lang.getMessage("gui.lore.page", Map.of("page", String.valueOf(page + 2)))),
                (player, click) -> {
                    page++;
                    refresh(player);
                }
            ));
        }
    }

    protected final void addBackButton(ClickAction action) {
        setButton((rows - 1) * 9 + 4, new Button(
            Material.COMPASS,
            lang.getMessage("gui.button.back"),
            null,
            action
        ));
    }

    private org.bukkit.plugin.java.JavaPlugin plugin() {
        return com.wykwey.beaconwaypoints.BeaconWaypoints.getInstance();
    }
}
