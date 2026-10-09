package fr.lifecraft.core;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** Tablist : header/footer et nom (préfixe LuckPerms + tag de faction). */
final class TabManager {

    private final LifeCore plugin;
    private final FactionHook factions;
    private final VaultHook vault;
    private BukkitTask task;

    TabManager(LifeCore plugin, FactionHook factions, VaultHook vault) {
        this.plugin = plugin;
        this.factions = factions;
        this.vault = vault;
    }

    void start() {
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("tablist.enabled", true)) {
            return;
        }
        long ticks = Math.max(1, c.getLong("tablist.update-seconds", 5)) * 20;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::update), 20L, ticks);
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        // Rend la tab par défaut
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
            p.playerListName(null);
        }
    }

    void update(Player p) {
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("tablist.enabled", true)) {
            return;
        }
        double tps = Math.min(20.0, Bukkit.getTPS()[0]);
        String tpsText = String.format(java.util.Locale.ROOT, "%.1f", Math.round(tps * 10) / 10.0);
        String header = fill(String.join("\n", c.getStringList("tablist.header")), p, tpsText);
        String footer = fill(String.join("\n", c.getStringList("tablist.footer")), p, tpsText);
        p.sendPlayerListHeaderAndFooter(Text.color(header), Text.color(footer));

        String tag = factions.tagOf(p);
        String fac = tag == null ? "" : c.getString("tablist.faction-format", " &8[&7{tag}&8]").replace("{tag}", tag);
        String prefix = vault != null ? vault.prefix(p) : "";
        String name = c.getString("tablist.name-format", "{prefix}{name}{faction}")
            .replace("{prefix}", prefix).replace("{name}", p.getName()).replace("{faction}", fac);
        p.playerListName(Text.color(name));
    }

    private String fill(String s, Player p, String tps) {
        return s.replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
            .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()))
            .replace("{tps}", tps)
            .replace("{ping}", String.valueOf(p.getPing()))
            .replace("{player}", p.getName());
    }
}
