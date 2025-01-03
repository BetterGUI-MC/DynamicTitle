package me.hsgamer.bettergui.dynamictitle;

import java.lang.reflect.Method;

public class InventoryViewUtil {
    private static final Class<?> inventoryViewClass;
    private static final Method getTopInventoryMethod;
    private static final Method getOriginalTitleMethod;
    private static final Method setTitleMethod;

    static {
        try {
            inventoryViewClass = Class.forName("org.bukkit.inventory.InventoryView");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        try {
            getTopInventoryMethod = inventoryViewClass.getMethod("getTopInventory");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        try {
            getOriginalTitleMethod = inventoryViewClass.getMethod("getOriginalTitle");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        try {
            setTitleMethod = inventoryViewClass.getMethod("setTitle", String.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getOriginalTitle(Object inventoryView) {
        try {
            return (String) getOriginalTitleMethod.invoke(inventoryView);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void setTitle(Object inventoryView, String title) {
        try {
            setTitleMethod.invoke(inventoryView, title);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Object getTopInventory(Object inventoryView) {
        try {
            return getTopInventoryMethod.invoke(inventoryView);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
