package fr.lifecraft.items;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

/**
 * Recettes : pièce en netherite au centre, Éclats de faille autour.
 * Découvertes automatiquement à la connexion (livre de recettes).
 */
final class RecipeManager implements Listener {

    /** Cases autour du centre, par ordre de remplissage quand moins de 8 éclats sont demandés. */
    private static final int[] FILL_ORDER = {1, 7, 3, 5, 0, 2, 6, 8};

    private final LifeItemsPlugin plugin;
    private final CustomItems items;
    private final List<NamespacedKey> keys = new ArrayList<>();

    RecipeManager(LifeItemsPlugin plugin, CustomItems items) {
        this.plugin = plugin;
        this.items = items;
    }

    List<NamespacedKey> keys() {
        return keys;
    }

    boolean isOurs(NamespacedKey key) {
        return key != null && key.getNamespace().equals(plugin.getName().toLowerCase(java.util.Locale.ROOT));
    }

    /** (Re)crée toutes les recettes selon la config. */
    void register(Settings settings) {
        unregister();
        int shards = settings.shardsPerItem;
        ItemStack shardItem = items.shard(1);
        for (String id : items.gearIds()) {
            Material base = items.baseMaterial(id);
            NamespacedKey key = new NamespacedKey(plugin, "craft_" + id);
            ShapedRecipe recipe = new ShapedRecipe(key, items.create(id, 1));
            recipe.shape(shape(shards));
            recipe.setIngredient('N', base);
            if (shards > 0) {
                recipe.setIngredient('E', new RecipeChoice.ExactChoice(shardItem));
            }
            Bukkit.addRecipe(recipe);
            keys.add(key);
        }
        for (var p : Bukkit.getOnlinePlayers()) {
            p.discoverRecipes(keys);
        }
    }

    void unregister() {
        for (NamespacedKey k : keys) {
            Bukkit.removeRecipe(k);
        }
        keys.clear();
    }

    /** Grille 3x3 : N au centre, E sur {@code shards} cases autour. */
    private static String[] shape(int shards) {
        char[] cells = new char[9];
        java.util.Arrays.fill(cells, ' ');
        cells[4] = 'N';
        for (int i = 0; i < shards && i < FILL_ORDER.length; i++) {
            cells[FILL_ORDER[i]] = 'E';
        }
        return new String[] {
            new String(cells, 0, 3), new String(cells, 3, 3), new String(cells, 6, 3)
        };
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipes(keys);
    }
}
