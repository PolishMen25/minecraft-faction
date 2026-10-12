package fr.lifecraft.shop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marque les inventaires de la boutique (menu principal : category == null). */
final class ShopHolder implements InventoryHolder {

    final Category category;
    final int page;
    private Inventory inventory;

    ShopHolder(Category category, int page) {
        this.category = category;
        this.page = page;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
