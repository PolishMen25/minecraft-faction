package fr.lifecraft.items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Catalogue des objets custom : création, identification par tag PersistentDataContainer. */
final class CustomItems {

    static final String SHARD_ID = "eclat_faille";
    private static final TextColor VIOLET = TextColor.color(0xAA55FF);
    private static final TextColor VIOLET_CLAIR = TextColor.color(0xD9A6FF);

    /** Définition d'une pièce d'équipement : base netherite + stats vanilla de départ. */
    private record Gear(String id, Material base, String name, EquipmentSlotGroup slot,
                        double armor, double toughness, double knockback,
                        double damage, double speed, String description) {
        boolean isArmor() {
            return armor > 0;
        }
    }

    private final LifeItemsPlugin plugin;
    private final NamespacedKey idKey;
    private final Map<String, Gear> gears = new LinkedHashMap<>();
    private Settings settings;

    CustomItems(LifeItemsPlugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.idKey = new NamespacedKey(plugin, "id"); // lifeitems:id
        // Valeurs de départ = netherite vanilla (modificateurs, base du joueur exclue).
        add(new Gear("casque_faille", Material.NETHERITE_HELMET, "Casque de faille", EquipmentSlotGroup.HEAD,
                3, 3, 0.1, 0, 0, "Forgé dans les profondeurs de la faille."));
        add(new Gear("plastron_faille", Material.NETHERITE_CHESTPLATE, "Plastron de faille", EquipmentSlotGroup.CHEST,
                8, 3, 0.1, 0, 0, "Forgé dans les profondeurs de la faille."));
        add(new Gear("jambieres_faille", Material.NETHERITE_LEGGINGS, "Jambières de faille", EquipmentSlotGroup.LEGS,
                6, 3, 0.1, 0, 0, "Forgées dans les profondeurs de la faille."));
        add(new Gear("bottes_faille", Material.NETHERITE_BOOTS, "Bottes de faille", EquipmentSlotGroup.FEET,
                3, 3, 0.1, 0, 0, "Forgées dans les profondeurs de la faille."));
        add(new Gear("epee_faille", Material.NETHERITE_SWORD, "Épée de faille", EquipmentSlotGroup.MAINHAND,
                0, 0, 0, 7, -2.4, "Sa lame vibre au contact de la faille."));
        add(new Gear("pioche_faille", Material.NETHERITE_PICKAXE, "Pioche de faille", EquipmentSlotGroup.MAINHAND,
                0, 0, 0, 5, -2.8, "Elle perce la roche comme du beurre."));
    }

    private void add(Gear g) {
        gears.put(g.id(), g);
    }

    void setSettings(Settings settings) {
        this.settings = settings;
    }

    /** Tous les ids connus (éclat en premier). */
    List<String> ids() {
        List<String> ids = new ArrayList<>();
        ids.add(SHARD_ID);
        ids.addAll(gears.keySet());
        return ids;
    }

    /** Ids des pièces d'équipement (sans l'éclat). */
    List<String> gearIds() {
        return new ArrayList<>(gears.keySet());
    }

    Material baseMaterial(String id) {
        Gear g = gears.get(id);
        return g == null ? null : g.base();
    }

    /** Id custom d'un item, ou null si ce n'est pas un objet LifeItems. */
    String idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    boolean isShard(ItemStack item) {
        return SHARD_ID.equals(idOf(item));
    }

    boolean isGear(ItemStack item) {
        String id = idOf(item);
        return id != null && gears.containsKey(id);
    }

    /** Crée un objet à partir de son id, ou null si l'id est inconnu. */
    ItemStack create(String id, int amount) {
        if (SHARD_ID.equals(id)) {
            return shard(amount);
        }
        Gear g = gears.get(id);
        if (g == null) {
            return null;
        }
        ItemStack item = new ItemStack(g.base(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain(g.name(), VIOLET));
        meta.lore(List.of(
                plain(g.description(), NamedTextColor.GRAY),
                Component.empty(),
                plain("Équipement de faille", VIOLET_CLAIR),
                plain("Réparable avec des Éclats de faille", NamedTextColor.DARK_GRAY)));
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, g.id());
        applyStats(meta, g);
        item.setItemMeta(meta);
        return item;
    }

    /** Éclat de faille : amethyst_shard renommé, empilable. */
    ItemStack shard(int amount) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain("Éclat de faille", VIOLET));
        meta.lore(List.of(
                plain("Un fragment cristallin arraché à la faille.", NamedTextColor.GRAY),
                Component.empty(),
                plain("Sert à forger et réparer l'équipement de faille", VIOLET_CLAIR)));
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, SHARD_ID);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Ajoute les modificateurs d'attributs. Dès qu'un modificateur est posé, ceux de la netherite
     * vanilla sont remplacés : on réécrit donc toutes les valeurs (base vanilla + bonus configurés).
     */
    private void applyStats(ItemMeta meta, Gear g) {
        if (g.isArmor()) {
            put(meta, Attribute.ARMOR, g, "armor", g.armor() + settings.armorBonus);
            put(meta, Attribute.ARMOR_TOUGHNESS, g, "toughness", g.toughness() + settings.toughnessBonus);
            put(meta, Attribute.KNOCKBACK_RESISTANCE, g, "knockback", g.knockback() + settings.knockbackBonus);
        } else {
            double bonus = g.base() == Material.NETHERITE_SWORD ? settings.swordDamageBonus : settings.pickaxeDamageBonus;
            put(meta, Attribute.ATTACK_DAMAGE, g, "damage", g.damage() + bonus);
            put(meta, Attribute.ATTACK_SPEED, g, "speed", g.speed());
        }
    }

    private void put(ItemMeta meta, Attribute attribute, Gear g, String suffix, double amount) {
        NamespacedKey key = new NamespacedKey(plugin, g.id() + "_" + suffix);
        meta.addAttributeModifier(attribute, new AttributeModifier(key, amount, Operation.ADD_NUMBER, g.slot()));
    }

    /** Texte sans italique (les noms/lores custom sont en italique par défaut). */
    private static Component plain(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

}
