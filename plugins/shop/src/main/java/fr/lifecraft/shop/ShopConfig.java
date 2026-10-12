package fr.lifecraft.shop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

/**
 * Configuration chargée et validée. Règles appliquées à chaque objet :
 * achat &gt; vente (sinon boucle d'argent) et seuil anti-boucle PNJ ; les objets fautifs sont ignorés.
 */
final class ShopConfig {

    final String menuTitle;
    final int menuRows;
    final Material filler;
    final int balanceSlot;
    final String categoryTitle;
    final String soundOk;
    final String soundError;
    final String soundNav;
    final String loreBuy;
    final String loreSell;
    final String loreNoSell;
    final List<String> loreHelp;
    final Map<String, String> messages;
    final List<Category> categories;
    final int accepted;
    final int ignored;

    ShopConfig(FileConfiguration cfg, Logger log) {
        menuTitle = cfg.getString("menu.titre", "&8Boutique");
        menuRows = Math.max(1, Math.min(6, cfg.getInt("menu.lignes", 4)));
        Material f = Material.matchMaterial(cfg.getString("menu.remplissage", "AIR"));
        filler = f == null ? Material.AIR : f;
        balanceSlot = cfg.getInt("menu.slot-solde", -1);
        categoryTitle = cfg.getString("menu.titre-categorie", "&8Boutique : {categorie} &7({page}/{pages})");
        soundOk = cfg.getString("sons.succes", "");
        soundError = cfg.getString("sons.erreur", "");
        soundNav = cfg.getString("sons.navigation", "");
        loreBuy = cfg.getString("lore.achat", "&7Achat : &a{achat}");
        loreSell = cfg.getString("lore.vente", "&7Vente : &c{vente}");
        loreNoSell = cfg.getString("lore.non-vendable", "&7Vente : &8non vendable");
        loreHelp = cfg.getStringList("lore.aide");

        Map<String, String> m = new HashMap<>();
        ConfigurationSection ms = cfg.getConfigurationSection("messages");
        if (ms != null) {
            for (String k : ms.getKeys(false)) {
                m.put(k, ms.getString(k, ""));
            }
        }
        messages = m;

        // Table anti-boucle PNJ : matériau -> quantité pour 1 émeraude
        double emeraldValue = cfg.getDouble("anti-boucle.valeur-emeraude", 180);
        double margin = cfg.getDouble("anti-boucle.marge", 1.2);
        Map<Material, Integer> npc = new LinkedHashMap<>();
        ConfigurationSection ns = cfg.getConfigurationSection("anti-boucle.quantite-pour-1-emeraude");
        if (ns != null) {
            for (String k : ns.getKeys(false)) {
                Material mat = Material.matchMaterial(k);
                if (mat == null || ns.getInt(k) <= 0) {
                    log.warning("anti-boucle : entrée invalide ignorée : " + k);
                    continue;
                }
                npc.put(mat, ns.getInt(k));
            }
        }
        double threshold = emeraldValue * margin;

        List<Category> cats = new ArrayList<>();
        int ok = 0;
        int ko = 0;
        Set<Integer> usedSlots = new HashSet<>();
        ConfigurationSection cs = cfg.getConfigurationSection("categories");
        if (cs != null) {
            int auto = 0;
            for (String id : cs.getKeys(false)) {
                ConfigurationSection c = cs.getConfigurationSection(id);
                if (c == null) {
                    continue;
                }
                Material icon = Material.matchMaterial(c.getString("icon", "STONE"));
                if (icon == null || icon.isAir()) {
                    icon = Material.STONE;
                }
                int slot = c.getInt("slot", -1);
                if (slot < 0 || slot >= menuRows * 9 || slot == balanceSlot || !usedSlots.add(slot)) {
                    while (auto < menuRows * 9 && (auto == balanceSlot || usedSlots.contains(auto))) {
                        auto++;
                    }
                    slot = auto;
                    usedSlots.add(slot);
                }
                List<ShopItem> items = new ArrayList<>();
                for (Map<?, ?> raw : c.getMapList("items")) {
                    String err = null;
                    ShopItem item = null;
                    try {
                        item = parse(raw);
                        err = check(item, npc, threshold);
                    } catch (IllegalArgumentException e) {
                        err = e.getMessage();
                    }
                    if (err != null) {
                        ko++;
                        log.warning("Objet ignoré (" + id + ") " + raw + " : " + err);
                    } else {
                        ok++;
                        items.add(item);
                    }
                }
                cats.add(new Category(id, c.getString("title", id), icon, slot,
                    c.getStringList("description"), List.copyOf(items)));
            }
        }
        categories = List.copyOf(cats);
        accepted = ok;
        ignored = ko;
    }

    private static ShopItem parse(Map<?, ?> raw) {
        Object mo = raw.get("material");
        Material mat = mo == null ? null : Material.matchMaterial(mo.toString());
        if (mat == null || !mat.isItem() || mat.isAir()) {
            throw new IllegalArgumentException("matériau inconnu");
        }
        double buy = number(raw.get("buy"), "buy");
        double sell = raw.get("sell") == null ? 0 : number(raw.get("sell"), "sell");
        EntityType spawner = null;
        if (raw.get("spawner") != null) {
            try {
                spawner = EntityType.valueOf(raw.get("spawner").toString().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("type de spawner inconnu");
            }
            if (mat != Material.SPAWNER) {
                throw new IllegalArgumentException("spawner: exige material: SPAWNER");
            }
        }
        String name = raw.get("name") == null ? null : raw.get("name").toString();
        return new ShopItem(mat, spawner, name, buy, sell);
    }

    private static double number(Object o, String field) {
        if (o instanceof Number n && Double.isFinite(n.doubleValue())) {
            return n.doubleValue();
        }
        throw new IllegalArgumentException(field + " absent ou non numérique");
    }

    /** Retourne le motif de rejet, ou null si l'objet est valide. */
    static String check(ShopItem item, Map<Material, Integer> npc, double threshold) {
        if (item.buy() <= 0) {
            return "prix d'achat <= 0";
        }
        if (item.sell() < 0) {
            return "prix de vente négatif";
        }
        if (item.sell() > 0 && item.buy() <= item.sell()) {
            return "achat (" + item.buy() + ") doit être strictement supérieur à la vente (" + item.sell() + ")";
        }
        Integer qty = npc.get(item.material());
        if (qty != null && item.buy() * qty < threshold) {
            return "anti-boucle PNJ : " + item.buy() + " x " + qty + " < " + threshold;
        }
        return null;
    }

    String msg(String key, String... pairs) {
        String s = messages.getOrDefault(key, key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            s = s.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return s;
    }

    Category category(String id) {
        for (Category c : categories) {
            if (c.id().equalsIgnoreCase(id)) {
                return c;
            }
        }
        return null;
    }
}
