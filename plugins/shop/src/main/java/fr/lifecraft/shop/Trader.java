package fr.lifecraft.shop;

import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Achats et ventes. Appelé uniquement depuis le thread principal (événements d'inventaire).
 * Principe : on ne débite jamais sans donner l'objet, on ne retire jamais sans payer.
 */
final class Trader {

    private final LifeShop plugin;

    Trader(LifeShop plugin) {
        this.plugin = plugin;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Objet « propre » : sans nom, lore, enchantement ni donnée PDC (donc pas un objet custom). */
    static boolean isPlain(ItemStack s) {
        if (s == null || !s.hasItemMeta()) {
            return true;
        }
        ItemMeta m = s.getItemMeta();
        return !m.hasDisplayName() && !m.hasItemName() && !m.hasLore() && !m.hasEnchants()
            && m.getPersistentDataContainer().getKeys().isEmpty();
    }

    /** Place libre pour cet objet dans les 36 emplacements de stockage. */
    private static int room(PlayerInventory inv, ItemStack product) {
        int max = product.getMaxStackSize();
        int room = 0;
        for (ItemStack s : inv.getStorageContents()) {
            if (s == null || s.getType().isAir()) {
                room += max;
            } else if (s.isSimilar(product)) {
                room += Math.max(0, max - s.getAmount());
            }
        }
        return room;
    }

    private String name(ShopItem it) {
        Component c = it.label();
        // Component.translatable : on passe par le nom du matériau pour le texte brut des messages
        return it.name() != null
            ? PlainTextComponentSerializer.plainText().serialize(c)
            : friendly(it.material().name());
    }

    private static String friendly(String enumName) {
        String s = enumName.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    void buy(Player p, ShopItem it, boolean stack) {
        ShopConfig c = plugin.shopConfig();
        Economy eco = plugin.economy();
        if (eco == null) {
            fail(p, c.msg("economie-absente"));
            return;
        }
        int want = stack ? it.stackSize() : 1;
        PlayerInventory inv = p.getInventory();
        int room = room(inv, it.create(1));
        if (room <= 0) {
            fail(p, c.msg("inventaire-plein"));
            return;
        }
        int qty = Math.min(want, room);
        double total = round2(it.buy() * qty);
        if (!eco.has(p, total)) {
            fail(p, c.msg("pas-assez-argent", "montant", Text.money(total), "solde", Text.money(eco.getBalance(p))));
            return;
        }
        EconomyResponse r = eco.withdrawPlayer(p, total);
        if (!r.transactionSuccess()) {
            fail(p, c.msg("erreur-economie"));
            return;
        }
        Map<Integer, ItemStack> left = inv.addItem(it.create(qty));
        int notGiven = 0;
        for (ItemStack s : left.values()) {
            notGiven += s.getAmount();
        }
        if (notGiven > 0) {
            // sécurité : rembourse ce qui n'a pas pu être donné
            double refund = round2(it.buy() * notGiven);
            eco.depositPlayer(p, refund);
            qty -= notGiven;
            total = round2(total - refund);
        }
        if (qty <= 0) {
            fail(p, c.msg("inventaire-plein"));
            return;
        }
        String label = name(it);
        ok(p, c.msg("achat-ok", "quantite", String.valueOf(qty), "objet", label, "montant", Text.money(total)));
        if (qty < want) {
            p.sendMessage(Text.color(c.msg("prefixe") + c.msg("achat-partiel",
                "quantite", String.valueOf(qty), "demande", String.valueOf(want))));
        }
        plugin.transactionLog().write(p.getName(), true, it.material().name()
            + (it.spawner() != null ? ":" + it.spawner().name() : ""), qty, total);
    }

    void sell(Player p, ShopItem it, boolean all) {
        ShopConfig c = plugin.shopConfig();
        if (!it.sellable()) {
            fail(p, c.msg("non-vendable"));
            return;
        }
        Economy eco = plugin.economy();
        if (eco == null) {
            fail(p, c.msg("economie-absente"));
            return;
        }
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        int have = 0;
        for (ItemStack s : contents) {
            if (s != null && s.getType() == it.material() && isPlain(s)) {
                have += s.getAmount();
            }
        }
        int qty = all ? have : Math.min(1, have);
        if (qty <= 0) {
            fail(p, c.msg("rien-a-vendre", "objet", name(it)));
            return;
        }
        // retire les objets en gardant une copie des piles d'origine pour pouvoir annuler
        Map<Integer, ItemStack> backup = new HashMap<>();
        int remaining = qty;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack s = contents[i];
            if (s == null || s.getType() != it.material() || !isPlain(s)) {
                continue;
            }
            backup.put(i, s.clone());
            int take = Math.min(remaining, s.getAmount());
            remaining -= take;
            if (take >= s.getAmount()) {
                inv.setItem(i, null);
            } else {
                ItemStack rest = s.clone();
                rest.setAmount(s.getAmount() - take);
                inv.setItem(i, rest);
            }
        }
        double total = round2(it.sell() * qty);
        EconomyResponse r = eco.depositPlayer(p, total);
        if (!r.transactionSuccess()) {
            for (Map.Entry<Integer, ItemStack> e : backup.entrySet()) {
                inv.setItem(e.getKey(), e.getValue());
            }
            fail(p, c.msg("erreur-economie"));
            return;
        }
        ok(p, c.msg("vente-ok", "quantite", String.valueOf(qty), "objet", name(it), "montant", Text.money(total)));
        plugin.transactionLog().write(p.getName(), false, it.material().name(), qty, total);
    }

    private void ok(Player p, String message) {
        p.sendMessage(Text.color(plugin.shopConfig().msg("prefixe") + message));
        plugin.play(p, plugin.shopConfig().soundOk);
    }

    private void fail(Player p, String message) {
        p.sendMessage(Text.color(plugin.shopConfig().msg("prefixe") + message));
        plugin.play(p, plugin.shopConfig().soundError);
    }
}
