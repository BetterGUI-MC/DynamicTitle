package me.hsgamer.bettergui.dynamictitle;

import me.hsgamer.bettergui.builder.InventoryBuilder;
import me.hsgamer.hscore.bukkit.addon.PluginAddon;
import me.hsgamer.hscore.bukkit.gui.GUIHolder;
import me.hsgamer.hscore.bukkit.gui.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

public final class DynamicTitle extends PluginAddon implements Listener {
    private final Map<Inventory, BukkitTask> inventoryTaskMap = new IdentityHashMap<>();

    @Override
    public void onEnable() {
        InventoryBuilder.INSTANCE.register(map -> {
            long period = Optional.ofNullable(map.get("title-period")).map(String::valueOf).map(Long::parseLong).orElse(-1L);
            return (display, uuid) -> {
                GUIHolder holder = display.getHolder();
                InventoryType type = holder.getInventoryType();
                int size = holder.getSize(uuid);
                String title = holder.getTitle(uuid);
                Inventory inventory = type == InventoryType.CHEST && size > 0
                        ? Bukkit.createInventory(display, GUIUtils.normalizeToChestSize(size), title)
                        : Bukkit.createInventory(display, type, title);

                if (period >= 0) {
                    inventoryTaskMap.put(inventory, Bukkit.getScheduler().runTaskTimer(getPlugin(), () -> {
                        String newTitle = holder.getTitle(uuid);
                        if (!newTitle.equals(title)) {
                            for (HumanEntity viewer : inventory.getViewers()) {
                                if (viewer instanceof Player) {
                                    InventoryUpdate.updateInventory((Player) viewer, newTitle);
                                }
                            }
                        }
                    }, period, period));
                }

                return inventory;
            };
        });

        Bukkit.getPluginManager().registerEvents(this, getPlugin());
    }

    private void clearAll() {
        inventoryTaskMap.values().forEach(BukkitTask::cancel);
        inventoryTaskMap.clear();
    }

    @Override
    public void onReload() {
        clearAll();
    }

    @Override
    public void onDisable() {
        clearAll();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        Optional.ofNullable(inventoryTaskMap.remove(event.getInventory())).ifPresent(BukkitTask::cancel);
    }
}
