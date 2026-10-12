package fr.lifecraft.core;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scheduler.BukkitTask;

/**
 * Scoreboard latéral : un scoreboard par joueur. Chaque ligne est une Team dont le préfixe porte
 * le texte (l'entrée est un code couleur invisible unique) : on ne change que les lignes modifiées,
 * donc pas de clignotement. Les numéros de droite sont masqués via le number-format de Paper.
 */
final class BoardManager {

    private static final int MAX_LINES = 15;

    /** Scoreboard d'un joueur et dernier texte envoyé pour chaque ligne. */
    private static final class Board {
        final Scoreboard scoreboard;
        final Objective objective;
        final List<Team> teams = new ArrayList<>();
        final String[] last = new String[MAX_LINES];
        String lastTitle;
        int lines;

        Board(Scoreboard sb, Objective o) {
            this.scoreboard = sb;
            this.objective = o;
        }
    }

    private final LifeCore plugin;
    private final FactionHook factions;
    private final VaultHook vault;
    private final NamespacedKey hiddenKey;
    private final Map<UUID, Board> boards = new HashMap<>();
    private BukkitTask task;

    BoardManager(LifeCore plugin, FactionHook factions, VaultHook vault) {
        this.plugin = plugin;
        this.factions = factions;
        this.vault = vault;
        this.hiddenKey = new NamespacedKey(plugin, "sb_hidden");
    }

    void start() {
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("scoreboard.enabled", true)) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            add(p);
        }
        long ticks = Math.max(1, c.getLong("scoreboard.update-seconds", 2)) * 20;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                update(p);
            }
        }, 20L, ticks);
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        clear();
    }

    /** Retire tous les scoreboards (les joueurs retrouvent le scoreboard principal). */
    void clear() {
        for (UUID id : new ArrayList<>(boards.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        boards.clear();
    }

    boolean isHidden(Player p) {
        return p.getPersistentDataContainer().has(hiddenKey, PersistentDataType.BYTE);
    }

    void setHidden(Player p, boolean hidden) {
        if (hidden) {
            p.getPersistentDataContainer().set(hiddenKey, PersistentDataType.BYTE, (byte) 1);
            remove(p);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        } else {
            p.getPersistentDataContainer().remove(hiddenKey);
            add(p);
        }
    }

    void add(Player p) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true) || isHidden(p)) {
            return;
        }
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = sb.registerNewObjective("lifecore", "dummy", Component.empty());
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        try {
            o.numberFormat(NumberFormat.blank());
        } catch (RuntimeException | LinkageError ignored) {
            // API absente : les numéros resteront visibles
        }
        boards.put(p.getUniqueId(), new Board(sb, o));
        p.setScoreboard(sb);
        update(p);
    }

    void remove(Player p) {
        boards.remove(p.getUniqueId());
    }

    private void update(Player p) {
        Board b = boards.get(p.getUniqueId());
        if (b == null) {
            return;
        }
        FileConfiguration c = plugin.getConfig();
        List<String> template = c.getStringList("scoreboard.lines");
        int n = Math.min(template.size(), MAX_LINES);

        String title = c.getString("scoreboard.title", "&6&lLifeCraft");
        if (!title.equals(b.lastTitle)) {
            b.objective.displayName(Text.color(title));
            b.lastTitle = title;
        }

        FactionHook.Info f = factions.info(p);
        String money = vault != null ? vault.money(p) : "?";
        String noFaction = c.getString("scoreboard.no-faction", "Aucune");

        // Ajuste le nombre de lignes si la config a changé
        while (b.teams.size() < n) {
            int i = b.teams.size();
            String entry = "§" + Integer.toHexString(i) + "§r";
            Team t = b.scoreboard.registerNewTeam("l" + i);
            t.addEntry(entry);
            b.teams.add(t);
        }
        while (b.lines > n) {
            b.lines--;
            b.scoreboard.resetScores(entry(b.lines));
            b.last[b.lines] = null;
        }
        b.lines = n;

        for (int i = 0; i < n; i++) {
            String line = template.get(i)
                .replace("{faction}", f.hasFaction() ? f.tag() : noFaction)
                .replace("{power}", f.power())
                .replace("{maxpower}", f.maxPower())
                .replace("{fpower}", f.factionPower())
                .replace("{fmaxpower}", f.factionMaxPower())
                .replace("{lands}", f.lands())
                .replace("{money}", money)
                .replace("{kills}", String.valueOf(p.getStatistic(Statistic.PLAYER_KILLS)))
                .replace("{deaths}", String.valueOf(p.getStatistic(Statistic.DEATHS)))
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{player}", p.getName());
            if (!line.equals(b.last[i])) {
                b.teams.get(i).prefix(Text.color(line));
                b.last[i] = line;
            }
            // Le score fixe l'ordre (haut = grand score) ; inchangé donc sans effet visuel
            b.objective.getScore(entry(i)).setScore(n - i);
        }
    }

    private static String entry(int i) {
        return "§" + Integer.toHexString(i) + "§r";
    }
}
