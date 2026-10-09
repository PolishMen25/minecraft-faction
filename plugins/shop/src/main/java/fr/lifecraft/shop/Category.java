package fr.lifecraft.shop;

import java.util.List;
import org.bukkit.Material;

/** Catégorie de la boutique : icône du menu principal et objets paginés. */
record Category(String id, String title, Material icon, int slot, List<String> description, List<ShopItem> items) {
}
