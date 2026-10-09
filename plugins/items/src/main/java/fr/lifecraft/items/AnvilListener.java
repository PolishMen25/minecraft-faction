package fr.lifecraft.items;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/** Réparation de l'équipement de faille à l'enclume avec des Éclats de faille. */
final class AnvilListener implements Listener {

    private final LifeItemsPlugin plugin;

    AnvilListener(LifeItemsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareAnvilEvent event) {
        Settings s = plugin.settings();
        AnvilInventory inv = event.getInventory();
        ItemStack first = inv.getItem(0);
        ItemStack second = inv.getItem(1);
        CustomItems items = plugin.items();

        if (!items.isShard(second) || !items.isGear(first)) {
            return;
        }
        if (!s.anvilEnabled) {
            event.setResult(null);
            return;
        }
        if (!(first.getItemMeta() instanceof Damageable dmg) || dmg.getDamage() <= 0) {
            event.setResult(null);
            return;
        }

        int max = dmg.hasMaxDamage() ? dmg.getMaxDamage() : first.getType().getMaxDurability();
        int perShard = Math.max(1, (int) Math.ceil(max * s.repairFraction));
        int needed = (int) Math.ceil(dmg.getDamage() / (double) perShard);
        int used = Math.max(1, Math.min(second.getAmount(), needed));

        ItemStack result = first.clone();
        Damageable resultMeta = (Damageable) result.getItemMeta();
        resultMeta.setDamage(Math.max(0, dmg.getDamage() - used * perShard));
        result.setItemMeta(resultMeta); // enchantements, nom et tag conservés
        event.setResult(result);
        inv.setRepairCostAmount(used);
        inv.setRepairCost(Math.max(1, used * s.levelsPerShard));
    }
}
