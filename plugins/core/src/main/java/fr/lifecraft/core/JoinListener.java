package fr.lifecraft.core;

import java.time.Duration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Connexion : scoreboard, tablist, bienvenue et title. */
final class JoinListener implements Listener {

    private final LifeCore plugin;
    private final BoardManager boards;
    private final TabManager tab;

    JoinListener(LifeCore plugin, BoardManager boards, TabManager tab) {
        this.plugin = plugin;
        this.boards = boards;
        this.tab = tab;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        boards.add(p);
        tab.update(p);

        if (!p.hasPlayedBefore()) {
            int number = uniquePlayers(p);
            String text = plugin.getConfig().getString("welcome.first-join-broadcast", "");
            if (!text.isBlank()) {
                Bukkit.broadcast(Text.color(text
                    .replace("{player}", p.getName()).replace("{number}", String.valueOf(number))));
            }
        }

        ConfigurationSection t = plugin.getConfig().getConfigurationSection("welcome.title");
        if (t != null && t.getBoolean("enabled", true)) {
            p.showTitle(Title.title(
                Text.color(t.getString("title", "").replace("{player}", p.getName())),
                Text.color(t.getString("subtitle", "").replace("{player}", p.getName())),
                Title.Times.times(
                    Duration.ofMillis(t.getInt("fade-in-ticks", 10) * 50L),
                    Duration.ofMillis(t.getInt("stay-ticks", 60) * 50L),
                    Duration.ofMillis(t.getInt("fade-out-ticks", 20) * 50L))));
        }
    }

    /** Nombre de joueurs uniques, en comptant le nouveau si son fichier n'existe pas encore. */
    private int uniquePlayers(Player p) {
        OfflinePlayer[] all = Bukkit.getOfflinePlayers();
        for (OfflinePlayer o : all) {
            if (o.getUniqueId().equals(p.getUniqueId())) {
                return all.length;
            }
        }
        return all.length + 1;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        boards.remove(e.getPlayer());
    }
}
