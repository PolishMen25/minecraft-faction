package fr.lifecraft.items;

import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Keyed;
import org.bukkit.inventory.Recipe;

/** Empêche d'utiliser un objet custom comme ingrédient d'une recette qui n'est pas la nôtre. */
final class CraftListener implements Listener {

    private final LifeItemsPlugin plugin;

    CraftListener(LifeItemsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        Recipe recipe = event.getRecipe();
        NamespacedKey key = recipe instanceof Keyed k ? k.getKey() : null;
        boolean ours = plugin.recipes().isOurs(key);

        for (ItemStack stack : inv.getMatrix()) {
            String id = plugin.items().idOf(stack);
            if (id == null) {
                continue;
            }
            // Dans nos recettes, seuls les éclats sont permis (la pièce centrale doit être de la netherite vanilla).
            if (!(ours && CustomItems.SHARD_ID.equals(id))) {
                inv.setResult(null);
                return;
            }
        }
    }
}
