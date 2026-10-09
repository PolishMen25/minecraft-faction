package fr.lifecraft.items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** /lifeitems give <joueur> <id> [quantité] | list | reload. */
final class LifeItemsCommand implements TabExecutor {

    private static final String PREFIX = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "[LifeItems] " + ChatColor.RESET;
    private static final String PERM = "lifeitems.admin";
    private static final int MAX_AMOUNT = 2304; // 36 piles de 64

    private final LifeItemsPlugin plugin;

    LifeItemsCommand(LifeItemsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission(PERM)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Permission refusée.");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> give(sender, args);
            case "list" -> list(sender);
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Configuration et recettes rechargées.");
            }
            default -> sender.sendMessage(PREFIX + ChatColor.YELLOW
                    + "/" + label + " give <joueur> <id> [quantité] | list | reload");
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Usage : /lifeitems give <joueur> <id> [quantité]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Joueur introuvable ou hors ligne : " + args[1]);
            return;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Quantité invalide : " + args[3]);
                return;
            }
            if (amount < 1 || amount > MAX_AMOUNT) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Quantité entre 1 et " + MAX_AMOUNT + ".");
                return;
            }
        }
        ItemStack item = plugin.items().create(id, 1);
        if (item == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Id inconnu : " + id + " (voir /lifeitems list)");
            return;
        }
        // Les pièces d'équipement ne s'empilent pas : une par emplacement.
        int stack = Math.max(1, item.getMaxStackSize());
        int left = amount;
        int dropped = 0;
        while (left > 0) {
            int n = Math.min(left, stack);
            Map<Integer, ItemStack> rest = target.getInventory().addItem(plugin.items().create(id, n));
            for (ItemStack r : rest.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), r);
                dropped += r.getAmount();
            }
            left -= n;
        }
        sender.sendMessage(PREFIX + ChatColor.GREEN + amount + " x " + id + " donné à " + target.getName()
                + (dropped > 0 ? " (" + dropped + " au sol, inventaire plein)" : "") + ".");
        if (!sender.equals(target)) {
            target.sendMessage(PREFIX + ChatColor.LIGHT_PURPLE + "Vous avez reçu " + amount + " x " + id + ".");
        }
    }

    private void list(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.LIGHT_PURPLE + "Objets disponibles :");
        for (String id : plugin.items().ids()) {
            ItemStack item = plugin.items().create(id, 1);
            Component name = item.getItemMeta().displayName();
            sender.sendMessage(Component.text("  - " + id + " : ").append(name == null ? Component.empty() : name));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission(PERM)) {
            return out;
        }
        if (args.length == 1) {
            out.addAll(List.of("give", "list", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            out.addAll(plugin.items().ids());
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            out.addAll(List.of("1", "8", "16", "64"));
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
