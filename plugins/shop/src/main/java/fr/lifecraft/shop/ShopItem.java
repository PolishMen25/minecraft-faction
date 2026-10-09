package fr.lifecraft.shop;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.block.CreatureSpawner;

/**
 * Un objet de la boutique. sell &lt;= 0 : non vendable. spawner : type d'entité si material = SPAWNER.
 */
record ShopItem(Material material, EntityType spawner, String name, double buy, double sell) {

    boolean sellable() {
        return sell > 0 && spawner == null;
    }

    /** Taille de pile pour « acheter une pile » (64 ou maximum de l'objet). */
    int stackSize() {
        return Math.max(1, material.getMaxStackSize());
    }

    /** Objet livré au joueur (jamais de nom ni de tag : l'objet est « propre »). */
    ItemStack create(int amount) {
        ItemStack stack = new ItemStack(material, amount);
        if (spawner != null) {
            ItemMeta meta = stack.getItemMeta();
            if (meta instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof CreatureSpawner cs) {
                cs.setSpawnedType(spawner);
                bsm.setBlockState(cs);
                stack.setItemMeta(bsm);
            }
        }
        return stack;
    }

    /** Nom pour les messages. */
    Component label() {
        return name != null ? Text.item(name) : Component.translatable(material.translationKey());
    }
}
