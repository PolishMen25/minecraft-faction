package fr.lifecraft.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** LifeCore : scoreboard, tablist, bienvenue, annonces et liens. */
public final class LifeCore extends JavaPlugin {

    private FactionHook factions;
    private VaultHook vault;
    private BoardManager boards;
    private TabManager tab;
    private BukkitTask announceTask;
    private int announceIndex;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        factions = new FactionHook(getLogger());
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            vault = new VaultHook();
        } else {
            getLogger().warning("Vault absent : argent et préfixes indisponibles.");
        }
        boards = new BoardManager(this, factions, vault);
        tab = new TabManager(this, factions, vault);
        getServer().getPluginManager().registerEvents(new JoinListener(this, boards, tab), this);
        start();
    }

    @Override
    public void onDisable() {
        stop();
        boards.clear();
    }

    private void start() {
        boards.start();
        tab.start();
        if (getConfig().getBoolean("announcements.enabled", true)) {
            long ticks = Math.max(1, getConfig().getLong("announcements.interval-minutes", 5)) * 60 * 20;
            announceTask = Bukkit.getScheduler().runTaskTimer(this, this::announce, ticks, ticks);
        }
    }

    private void stop() {
        boards.stop();
        tab.stop();
        if (announceTask != null) {
            announceTask.cancel();
            announceTask = null;
        }
    }

    private void announce() {
        var list = getConfig().getStringList("announcements.messages");
        if (list.isEmpty() || Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        String msg = list.get(announceIndex++ % list.size());
        Bukkit.broadcast(Text.color(getConfig().getString("announcements.prefix", "") + msg));
    }

    private void reload() {
        stop();
        reloadConfig();
        start();
    }

    private void sendLink(CommandSender to, String key) {
        String url = getConfig().getString("links." + key + ".url", "");
        String msg = getConfig().getString("links." + key + ".message", "{url}").replace("{url}", url);
        Component c = Text.color(msg)
            .clickEvent(ClickEvent.openUrl(url))
            .hoverEvent(HoverEvent.showText(Text.color(getConfig().getString("links.hover", ""))));
        to.sendMessage(c);
    }

    private void msg(CommandSender to, String key) {
        to.sendMessage(Text.color(getConfig().getString("messages." + key, key)));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "discord" -> sendLink(sender, "discord");
            case "site" -> sendLink(sender, "site");
            case "sb" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "player-only");
                    return true;
                }
                boolean hidden = !boards.isHidden(p);
                boards.setHidden(p, hidden);
                msg(p, hidden ? "sb-hidden" : "sb-shown");
            }
            case "lifecore" -> {
                if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                    reload();
                    msg(sender, "reloaded");
                } else {
                    sender.sendMessage("/lifecore reload");
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
