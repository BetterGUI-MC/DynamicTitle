package me.hsgamer.bettergui.dynamictitle;

import me.hsgamer.bettergui.api.menu.Menu;
import me.hsgamer.bettergui.builder.InventoryBuilder;
import me.hsgamer.bettergui.util.MapUtil;
import me.hsgamer.bettergui.util.StringReplacerApplier;
import me.hsgamer.hscore.bukkit.addon.PluginAddon;
import me.hsgamer.hscore.bukkit.gui.GUIDisplay;
import me.hsgamer.hscore.bukkit.gui.GUIHolder;
import me.hsgamer.hscore.bukkit.gui.GUIUtils;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class DynamicTitle extends PluginAddon implements Listener {
    private static final String ORIGINAL_KEY = "%original%";
    private final Map<Inventory, InventoryUpdateData> inventoryMap = new IdentityHashMap<>();
    private final Set<BukkitTask> tasks = new HashSet<>();

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
                GUIHolder holder = display.getHolder();
                InventoryType type = holder.getInventoryType();
                int size = holder.getSize(uuid);
                String title = holder.getTitle(uuid);
                Inventory inventory = type == InventoryType.CHEST && size > 0
                        ? Bukkit.createInventory(display, GUIUtils.normalizeToChestSize(size), title)
                        : Bukkit.createInventory(display, type, title);

                if (data.period >= 0) {
                    inventoryMap.put(inventory, data);
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
        if (!inventoryMap.containsKey(inventory)) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof GUIDisplay)) return;
        GUIDisplay display = (GUIDisplay) holder;
        HumanEntity entity = event.getPlayer();
        if (!(entity instanceof Player)) return;
        Player player = (Player) entity;

        InventoryUpdateData data = inventoryMap.get(inventory);
        BukkitRunnable runnable = new BukkitRunnable() {
            private final AtomicInteger index = new AtomicInteger(0);

            @Override
            public void run() {
                if (!player.isOnline() || !player.isValid() || player.getOpenInventory().getTopInventory() != inventory) {
                    cancel();
                    return;
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
            }
        };
        tasks.add(runnable.runTaskTimer(getPlugin(), 0, data.period));
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
