package me.hsgamer.bettergui.dynamictitle;

import me.hsgamer.bettergui.api.addon.GetPlugin;
import me.hsgamer.bettergui.api.addon.Reloadable;
import me.hsgamer.bettergui.api.menu.Menu;
import me.hsgamer.bettergui.builder.InventoryBuilder;
import me.hsgamer.bettergui.util.StringReplacerApplier;
import me.hsgamer.hscore.bukkit.gui.BukkitGUIDisplay;
import me.hsgamer.hscore.bukkit.gui.BukkitGUIUtils;
import me.hsgamer.hscore.bukkit.scheduler.Scheduler;
import me.hsgamer.hscore.bukkit.scheduler.Task;
import me.hsgamer.hscore.common.CollectionUtils;
import me.hsgamer.hscore.common.MapUtils;
import me.hsgamer.hscore.expansion.common.Expansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

public final class DynamicTitle implements Expansion, GetPlugin, Reloadable, Listener {
    private static final String ORIGINAL_KEY = "%original%";
    private final Map<Inventory, InventoryUpdateData> inventoryMap = new IdentityHashMap<>();
    private final Set<Task> tasks = new HashSet<>();

    @Override
    public void onEnable() {
        InventoryBuilder.INSTANCE.register(pair -> {
            Menu menu = pair.getKey();
            Map<String, Object> map = pair.getValue();
            long period = Optional.ofNullable(MapUtils.getIfFound(map, "title-period", "title-update"))
                    .map(String::valueOf)
                    .map(Long::parseLong)
                    .orElse(0L);
            List<String> template = Optional.ofNullable(MapUtils.getIfFound(map, "title-template"))
                    .map(o -> CollectionUtils.createStringListFromObject(o, false))
                    .orElse(Collections.singletonList(ORIGINAL_KEY));
            InventoryUpdateData data = new InventoryUpdateData(period, template, menu);

            String title = Optional.ofNullable(MapUtils.getIfFound(map, "name", "title"))
                    .map(String::valueOf)
                    .orElse("");

            return BukkitGUIUtils.getInventoryFunctionFromTitle(uuid -> StringReplacerApplier.replace(title, uuid, menu))
                    .andThen(inventory -> {
                        if (data.period >= 0) {
                            inventoryMap.put(inventory, data);
                        }
                        return inventory;
                    });
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
        HumanEntity entity = event.getPlayer();
        if (!(entity instanceof Player)) return;
        Player player = (Player) entity;

        InventoryUpdateData data = inventoryMap.get(inventory);
        BooleanSupplier runnable = new BooleanSupplier() {
            private final AtomicInteger index = new AtomicInteger(0);

            @Override
            public boolean getAsBoolean() {
                InventoryView view = player.getOpenInventory();
                if (!player.isOnline() || !player.isValid() || view.getTopInventory() != inventory) {
                    return false;
                }
                int currentIndex = index.getAndIncrement();
                if (currentIndex >= data.template.size()) {
                    index.set(1);
                    currentIndex = 0;
                }
                String title = data.template.get(currentIndex);
                String originalTitle = view.getOriginalTitle();
                title = title.replace(ORIGINAL_KEY, originalTitle);
                title = StringReplacerApplier.replace(title, player.getUniqueId(), data.menu);
                view.setTitle(title);
                return true;
            }
        };
        tasks.add(Scheduler.current().sync().runEntityTaskTimer(player, runnable, 0, data.period));
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
