package fr.lifecraft.core;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Accès à Vault (économie + chat). Les services sont cherchés à chaque fois tant qu'ils
 * manquent, car EssentialsX et LuckPerms les enregistrent parfois après LifeCore.
 * Ne doit être instancié que si Vault est présent (sinon NoClassDefFoundError).
 */
final class VaultHook {

    private Economy economy;
    private Chat chat;

    private Economy economy() {
        if (economy == null) {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
            }
        }
        return economy;
    }

    private Chat chat() {
        if (chat == null) {
            RegisteredServiceProvider<Chat> rsp = Bukkit.getServicesManager().getRegistration(Chat.class);
            if (rsp != null) {
                chat = rsp.getProvider();
            }
        }
        return chat;
    }

    /** Solde formaté, ou « ? » si aucune économie. */
    String money(Player p) {
        Economy e = economy();
        if (e == null) {
            return "?";
        }
        try {
            return Text.money(e.getBalance(p));
        } catch (RuntimeException ex) {
            return "?";
        }
    }

    /** Préfixe de grade (codes & ou §), vide si absent. */
    String prefix(Player p) {
        Chat c = chat();
        if (c == null) {
            return "";
        }
        try {
            String s = c.getPlayerPrefix(p);
            return s == null ? "" : s;
        } catch (RuntimeException ex) {
            return "";
        }
    }
}
