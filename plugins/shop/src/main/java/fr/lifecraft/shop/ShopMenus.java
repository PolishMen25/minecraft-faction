package fr.lifecraft.shop;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Bukkit;

/** Construction des menus (principal et catégories paginées). */
final class ShopMenus {

    /** 5 lignes d'objets (0-44), la dernière ligne sert à la navigation. */
    static final int PER_PAGE = 45;
    static final int SLOT_PREV = 45;
    static final int SLOT_BACK = 47;
    static final int SLOT_BALANCE = 49;
    static final int SLOT_INFO = 51;
    static final int SLOT_NEXT = 53;

    private final LifeShop plugin;

    ShopMenus(LifeShop plugin) {
        this.plugin = plugin;
    }

    void openMain(Player p) {
        ShopConfig c = plugin.shopConfig();
        ShopHolder holder = new ShopHolder(null, 0);
        Inventory inv = Bukkit.createInventory(holder, c.menuRows * 9, Text.color(c.menuTitle));
        holder.setInventory(inv);
        if (c.filler != Material.AIR) {
            ItemStack f = simple(c.filler, " ", List.of());
            for (int i = 0; i < inv.getSize(); i++) {
                inv.setItem(i, f);
            }
        }
        for (Category cat : c.categories) {
            List<String> lore = new ArrayList<>(cat.description());
            lore.add("");
            lore.add("&7" + cat.items().size() + " objets");
            lore.add("&eClique pour ouvrir");
            inv.setItem(cat.slot(), simple(cat.icon(), cat.title(), lore));
        }
        if (c.balanceSlot >= 0 && c.balanceSlot < inv.getSize()) {
            inv.setItem(c.balanceSlot, balanceItem(p));
        }
        p.openInventory(inv);
    }

    void openCategory(Player p, Category cat, int page) {
        ShopConfig c = plugin.shopConfig();
        int pages = Math.max(1, (cat.items().size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.max(0, Math.min(page, pages - 1));
        ShopHolder holder = new ShopHolder(cat, pg);
        String title = c.categoryTitle.replace("{categorie}", cat.title())
            .replace("{page}", String.valueOf(pg + 1)).replace("{pages}", String.valueOf(pages));
        Inventory inv = Bukkit.createInventory(holder, 54, Text.color(title));
        holder.setInventory(inv);
        int from = pg * PER_PAGE;
        for (int i = 0; i < PER_PAGE && from + i < cat.items().size(); i++) {
            inv.setItem(i, itemIcon(c, cat.items().get(from + i)));
        }
        if (c.filler != Material.AIR) {
            ItemStack f = simple(c.filler, " ", List.of());
            for (int i = PER_PAGE; i < 54; i++) {
                inv.setItem(i, f);
            }
        }
        if (pg > 0) {
            inv.setItem(SLOT_PREV, simple(Material.ARROW, "&ePage précédente", List.of()));
        }
        if (pg < pages - 1) {
            inv.setItem(SLOT_NEXT, simple(Material.ARROW, "&ePage suivante", List.of()));
        }
        inv.setItem(SLOT_BACK, simple(Material.BARRIER, "&cRetour", List.of("&7Menu principal")));
        inv.setItem(SLOT_BALANCE, balanceItem(p));
        inv.setItem(SLOT_INFO, simple(Material.PAPER, "&7Page " + (pg + 1) + "/" + pages, List.of()));
        p.openInventory(inv);
    }

    /** Met à jour l'indicateur de solde du menu ouvert. */
    void refreshBalance(Player p, Inventory inv, int slot) {
        if (slot >= 0 && slot < inv.getSize()) {
            inv.setItem(slot, balanceItem(p));
        }
    }

    private ItemStack balanceItem(Player p) {
        Economy eco = plugin.economy();
        String bal = "?";
        if (eco != null) {
            try {
                bal = Text.money(eco.getBalance(p));
            } catch (RuntimeException ignored) {
                // économie indisponible : on affiche « ? »
            }
        }
        return simple(Material.GOLD_INGOT, "&6Ton solde", List.of("&f" + bal));
    }

    private ItemStack itemIcon(ShopConfig c, ShopItem it) {
        ItemStack stack = it.create(1);
        ItemMeta meta = stack.getItemMeta();
        if (it.name() != null) {
            meta.displayName(Text.item(it.name()));
        }
        List<Component> lore = new ArrayList<>();
        lore.add(Text.item(c.loreBuy.replace("{achat}", Text.money(it.buy()))));
        if (it.sellable()) {
            lore.add(Text.item(c.loreSell.replace("{vente}", Text.money(it.sell()))));
        } else {
            lore.add(Text.item(c.loreNoSell));
        }
        for (String l : c.loreHelp) {
            if (!it.sellable() && (l.contains("vendre"))) {
                continue;
            }
            lore.add(Text.item(l.replace("{pile}", String.valueOf(it.stackSize()))));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack simple(Material m, String name, List<String> lore) {
        ItemStack s = new ItemStack(m);
        ItemMeta meta = s.getItemMeta();
        meta.displayName(Text.item(name));
        if (!lore.isEmpty()) {
            List<Component> l = new ArrayList<>();
            for (String x : lore) {
                l.add(Text.item(x));
            }
            meta.lore(l);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        s.setItemMeta(meta);
        return s;
    }
}
