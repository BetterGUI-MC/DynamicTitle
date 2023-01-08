package me.hsgamer.bettergui.dynamictitle;

import me.hsgamer.bettergui.builder.InventoryBuilder;
import me.hsgamer.hscore.bukkit.addon.PluginAddon;
import me.hsgamer.hscore.bukkit.gui.GUIDisplay;
import me.hsgamer.hscore.bukkit.gui.GUIHolder;
import me.hsgamer.hscore.bukkit.gui.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class DynamicTitle extends PluginAddon implements Listener {
    private final Map<Inventory, Long> inventoryMap = new IdentityHashMap<>();
    private final Set<BukkitTask> tasks = new HashSet<>();

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
                    inventoryMap.put(inventory, period);
                }

                return inventory;
            };
        }, "dynamic-title");

        Bukkit.getPluginManager().registerEvents(this, getPlugin());
    }

    @Override
    public void onReload() {
        inventoryMap.clear();
    }

    @Override
    public void onDisable() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        inventoryMap.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        Inventory inventory = event.getInventory();
        if (!inventoryMap.containsKey(inventory)) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof GUIDisplay)) return;
        GUIDisplay display = (GUIDisplay) holder;
        HumanEntity entity = event.getPlayer();
        if (!(entity instanceof Player)) return;
        Player player = (Player) entity;

        long period = inventoryMap.get(inventory);
        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !player.isValid() || player.getOpenInventory().getTopInventory() != inventory) {
                    cancel();
                    return;
                }
                String title = display.getHolder().getTitle(player.getUniqueId());
                InventoryUpdate.updateInventory(player, title);
            }
        };
        tasks.add(runnable.runTaskTimer(getPlugin(), period, period));
    }
}
