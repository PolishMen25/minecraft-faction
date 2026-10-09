package fr.lifecraft.shop;

import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.meta.BlockStateMeta;

/**
 * Clics dans les menus. TOUT est annulé dès que le menu du haut est un menu de la boutique
 * (clics normaux, shift, touches numériques, double-clic, glisser, clics dans l'inventaire du joueur),
 * seul le traitement ci-dessous agit ensuite.
 */
final class ShopListener implements Listener {

    private final LifeShop plugin;

    ShopListener(LifeShop plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        // getInventory() = inventaire du haut de la vue
        if (!(e.getInventory().getHolder() instanceof ShopHolder holder)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) {
            return;
        }
        ClickType t = e.getClick();
        if (t != ClickType.LEFT && t != ClickType.SHIFT_LEFT && t != ClickType.RIGHT && t != ClickType.SHIFT_RIGHT) {
            return;
        }
        int slot = e.getRawSlot();
        ShopConfig c = plugin.shopConfig();
        if (holder.category == null) {
            for (Category cat : c.categories) {
                if (cat.slot() == slot) {
                    plugin.play(p, c.soundNav);
                    plugin.menus().openCategory(p, cat, 0);
                    return;
                }
            }
            return;
        }
        Category cat = holder.category;
        if (slot == ShopMenus.SLOT_BACK) {
            plugin.play(p, c.soundNav);
            plugin.menus().openMain(p);
            return;
        }
        if (slot == ShopMenus.SLOT_PREV && t == ClickType.LEFT && holder.page > 0) {
            plugin.play(p, c.soundNav);
            plugin.menus().openCategory(p, cat, holder.page - 1);
            return;
        }
        if (slot == ShopMenus.SLOT_NEXT && t == ClickType.LEFT) {
            plugin.play(p, c.soundNav);
            plugin.menus().openCategory(p, cat, holder.page + 1);
            return;
        }
        if (slot < 0 || slot >= ShopMenus.PER_PAGE) {
            return;
        }
        int index = holder.page * ShopMenus.PER_PAGE + slot;
        if (index >= cat.items().size()) {
            return;
        }
        ShopItem item = cat.items().get(index);
        boolean shift = t == ClickType.SHIFT_LEFT || t == ClickType.SHIFT_RIGHT;
        if (t == ClickType.LEFT || t == ClickType.SHIFT_LEFT) {
            plugin.trader().buy(p, item, shift);
        } else {
            plugin.trader().sell(p, item, shift);
        }
        plugin.menus().refreshBalance(p, e.getInventory(), ShopMenus.SLOT_BALANCE);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof ShopHolder) {
            e.setCancelled(true);
        }
    }

    /**
     * Spawners achetés : depuis la 1.20, le jeu ignore les données de bloc d'un spawner posé par un
     * joueur non-op (il redevient un spawner à cochon). On recopie donc le type de l'objet sur le bloc.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaceSpawner(BlockPlaceEvent e) {
        if (e.getBlockPlaced().getType() != Material.SPAWNER
                || !(e.getItemInHand().getItemMeta() instanceof BlockStateMeta bsm)
                || !(bsm.getBlockState() instanceof CreatureSpawner fromItem)) {
            return;
        }
        EntityType type = fromItem.getSpawnedType();
        if (type == null) {
            return;
        }
        if (e.getBlockPlaced().getState() instanceof CreatureSpawner placed && placed.getSpawnedType() != type) {
            placed.setSpawnedType(type);
            placed.update(true, false);
        }
    }
}
