package me.hsgamer.bettergui.dynamictitle;

import me.hsgamer.bettergui.api.menu.Menu;
import me.hsgamer.bettergui.builder.InventoryBuilder;
import me.hsgamer.bettergui.util.MapUtil;
import me.hsgamer.bettergui.util.StringReplacerApplier;
import me.hsgamer.hscore.bukkit.addon.PluginAddon;
import me.hsgamer.hscore.bukkit.gui.BukkitGUIDisplay;
import me.hsgamer.hscore.bukkit.gui.BukkitGUIHolder;
import me.hsgamer.hscore.bukkit.gui.BukkitGUIUtils;
import me.hsgamer.hscore.bukkit.scheduler.Scheduler;
import me.hsgamer.hscore.bukkit.scheduler.Task;
import me.hsgamer.hscore.common.CollectionUtils;
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

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

public final class DynamicTitle extends PluginAddon implements Listener {
    private static final String ORIGINAL_KEY = "%original%";
    private final Map<BukkitGUIDisplay, InventoryUpdateData> inventoryMap = new IdentityHashMap<>();
    private final Set<Task> tasks = new HashSet<>();

    @Override
    public void onEnable() {
        InventoryBuilder.INSTANCE.register(pair -> {
            Menu menu = pair.getKey();
            Map<String, Object> map = pair.getValue();
            long period = Optional.ofNullable(MapUtil.getIfFound(map, "title-period", "title-update"))
                    .map(String::valueOf)
                    .map(Long::parseLong)
                    .orElse(0L);
            List<String> template = Optional.ofNullable(MapUtil.getIfFound(map, "title-template"))
                    .map(o -> CollectionUtils.createStringListFromObject(o, false))
                    .orElse(Collections.singletonList(ORIGINAL_KEY));
            InventoryUpdateData data = new InventoryUpdateData(period, template, menu);

            return (display, uuid) -> {
                BukkitGUIHolder holder = display.getHolder();
                InventoryType type = holder.getInventoryType();
                int size = holder.getSize(uuid);
                String title = holder.getTitle(uuid);
                Inventory inventory = type == InventoryType.CHEST && size > 0
                        ? Bukkit.createInventory(display, BukkitGUIUtils.normalizeToChestSize(size), title)
                        : Bukkit.createInventory(display, type, title);

                if (data.period >= 0) {
                    inventoryMap.put(display, data);
                }

                return inventory;
            };
        }, "dynamic-title");

        Bukkit.getPluginManager().registerEvents(this, getPlugin());
    }

    private void clearAll() {
        tasks.forEach(task -> {
            try {
                task.cancel();
            } catch (Exception ignored) {
                // IGNORED
            }
        });
        tasks.clear();
        inventoryMap.clear();
    }

    @Override
    public void onReload() {
        clearAll();
    }

    @Override
    public void onDisable() {
        clearAll();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();
        if (!(holder instanceof BukkitGUIDisplay)) return;
        BukkitGUIDisplay display = (BukkitGUIDisplay) holder;
        if (!inventoryMap.containsKey(display)) return;
        HumanEntity entity = event.getPlayer();
        if (!(entity instanceof Player)) return;
        Player player = (Player) entity;

        InventoryUpdateData data = inventoryMap.get(display);
        BooleanSupplier runnable = new BooleanSupplier() {
            private final AtomicInteger index = new AtomicInteger(0);

            @Override
            public boolean getAsBoolean() {
                if (!player.isOnline() || !player.isValid() || player.getOpenInventory().getTopInventory() != inventory) {
                    return false;
                }
                int currentIndex = index.getAndIncrement();
                if (currentIndex >= data.template.size()) {
                    index.set(1);
                    currentIndex = 0;
                }
                String title = data.template.get(currentIndex);
                String originalTitle = display.getHolder().getTitle(player.getUniqueId());
                title = title.replace(ORIGINAL_KEY, originalTitle);
                title = StringReplacerApplier.replace(title, player.getUniqueId(), data.menu);
                InventoryUpdate.updateInventory(player, title);
                return true;
            }
        };
        tasks.add(Scheduler.CURRENT.runEntityTaskTimer(getPlugin(), player, runnable, 0, data.period, false));
    }

    private static final class InventoryUpdateData {
        private final long period;
        private final List<String> template;
        private final Menu menu;

        private InventoryUpdateData(long period, List<String> template, Menu menu) {
            this.period = period;
            this.template = template;
            this.menu = menu;
        }
    }
}
