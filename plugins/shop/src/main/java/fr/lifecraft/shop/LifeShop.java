package fr.lifecraft.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** LifeShop : boutique en menus (achat/vente) branchée sur Vault. */
public final class LifeShop extends JavaPlugin {

    private ShopConfig config;
    private ShopMenus menus;
    private Trader trader;
    private TransactionLog txLog;
    private Economy economy;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        txLog = new TransactionLog(getDataFolder().toPath().resolve("transactions.log"), getLogger());
        menus = new ShopMenus(this);
        trader = new Trader(this);
        loadShop();
        Bukkit.getPluginManager().registerEvents(new ShopListener(this), this);
        if (economy() == null) {
            getLogger().warning("Aucune économie Vault pour l'instant (EssentialsX chargé après ?) : nouvelle recherche à chaque transaction.");
        }
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof ShopHolder) {
                p.closeInventory();
            }
        }
        if (txLog != null) {
            txLog.close();
        }
    }

    private void loadShop() {
        config = new ShopConfig(getConfig(), getLogger());
        getLogger().info(config.accepted + " objets chargés, " + config.ignored + " ignorés, "
            + config.categories.size() + " catégories.");
    }

    ShopConfig shopConfig() {
        return config;
    }

    ShopMenus menus() {
        return menus;
    }

    Trader trader() {
        return trader;
    }

    TransactionLog transactionLog() {
        return txLog;
    }

    /** Économie Vault, cherchée à chaque fois tant qu'elle manque. */
    Economy economy() {
        if (economy == null) {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
            }
        }
        return economy;
    }

    /** Joue un son par son nom (ex. ENTITY_EXPERIENCE_ORB_PICKUP) ; ignoré si vide. */
    void play(Player p, String sound) {
        if (sound == null || sound.isBlank()) {
            return;
        }
        p.playSound(p.getLocation(), sound.toLowerCase(Locale.ROOT).replace('_', '.'), 1f, 1f);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("lifeshop.admin")) {
                sender.sendMessage(Text.color(config.msg("prefixe") + config.msg("pas-permission")));
                return true;
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getOpenInventory().getTopInventory().getHolder() instanceof ShopHolder) {
                    p.closeInventory();
                }
            }
            reloadConfig();
            loadShop();
            sender.sendMessage(Text.color(config.msg("prefixe") + config.msg("reload-ok",
                "objets", String.valueOf(config.accepted), "ignores", String.valueOf(config.ignored))));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(config.msg("joueur-seulement"));
            return true;
        }
        if (args.length >= 1) {
            Category cat = config.category(args[0]);
            if (cat == null) {
                p.sendMessage(Text.color(config.msg("prefixe") + config.msg("categorie-inconnue")));
                return true;
            }
            menus.openCategory(p, cat, 0);
            return true;
        }
        menus.openMain(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String a = args[0].toLowerCase(Locale.ROOT);
            for (Category c : config.categories) {
                if (c.id().startsWith(a)) {
                    out.add(c.id());
                }
            }
            if (sender.hasPermission("lifeshop.admin") && "reload".startsWith(a)) {
                out.add("reload");
            }
        }
        return out;
    }
}
