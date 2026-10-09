package fr.lifecraft.koth;

import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

/**
 * Classement affiché dans le monde (TextDisplay vanilla), mis à jour toutes les 60 s.
 * L'UUID de l'entité est gardé dans outposts.yml ; l'entité porte aussi un tag
 * PersistentDataContainer pour retrouver (et supprimer) les doublons après un redémarrage.
 * Si le chunk n'est pas chargé, on ne fait rien : pas de chargement forcé.
 */
final class Leaderboard {

    private static final String DATA_KEY = "leaderboard-entity";
    private static final String SCOREBOARD_TAG = "lifekoth_classement";

    private final KothPlugin plugin;
    private final OutpostManager outposts;
    private final NamespacedKey tag;

    Leaderboard(KothPlugin plugin, OutpostManager outposts) {
        this.plugin = plugin;
        this.outposts = outposts;
        this.tag = new NamespacedKey(plugin, "classement");
    }

    void enable() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::update, 20L * 15, 20L * 60);
    }

    /** Position enregistrée dans la config, ou null si le monde est introuvable. */
    private Location location() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("leaderboard");
        if (s == null) {
            return null;
        }
        World w = Bukkit.getWorld(s.getString("world", "world"));
        return w == null ? null : new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"));
    }

    /** Met à jour le texte, ou recrée l'affichage si son chunk est chargé mais qu'il a disparu. */
    void update() {
        if (!plugin.getConfig().getBoolean("leaderboard.enabled", false)) {
            return;
        }
        Location loc = location();
        UUID saved = savedUuid();
        TextDisplay keep = saved != null && Bukkit.getEntity(saved) instanceof TextDisplay td && td.isValid() ? td : null;
        if (loc != null && isLoaded(loc)) {
            // Supprime les doublons autour de la position (ex. après un redémarrage)
            for (TextDisplay td : tagged(loc)) {
                if (keep == null) {
                    keep = td;
                } else if (!td.getUniqueId().equals(keep.getUniqueId())) {
                    td.remove();
                }
            }
            if (keep == null) {
                keep = loc.getWorld().spawn(loc, TextDisplay.class, td -> {
                    td.setBillboard(Display.Billboard.CENTER);
                    td.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
                    td.setLineWidth(260);
                    td.setShadowed(true);
                    td.setPersistent(true);
                    td.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
                    td.addScoreboardTag(SCOREBOARD_TAG);
                });
            }
        }
        if (keep == null) {
            return; // chunk non chargé : rien à faire
        }
        if (!keep.getUniqueId().equals(saved)) {
            outposts.data().set(DATA_KEY, keep.getUniqueId().toString());
            outposts.saveData();
        }
        keep.text(LegacyComponentSerializer.legacySection().deserialize(buildText()));
    }

    /** Enregistre une nouvelle position et (re)crée l'affichage. */
    void place(World world, double x, double y, double z) {
        remove();
        plugin.getConfig().set("leaderboard.enabled", true);
        plugin.getConfig().set("leaderboard.world", world.getName());
        plugin.getConfig().set("leaderboard.x", x);
        plugin.getConfig().set("leaderboard.y", y);
        plugin.getConfig().set("leaderboard.z", z);
        plugin.saveConfig();
        update();
    }

    /** Retire l'affichage (s'il est chargé) et désactive le classement. */
    void disable() {
        remove();
        plugin.getConfig().set("leaderboard.enabled", false);
        plugin.saveConfig();
        outposts.data().set(DATA_KEY, null);
        outposts.saveData();
    }

    private void remove() {
        Location loc = location();
        if (loc != null && isLoaded(loc)) {
            tagged(loc).forEach(Entity::remove);
        }
        UUID saved = savedUuid();
        Entity e = saved != null ? Bukkit.getEntity(saved) : null;
        if (e != null) {
            e.remove();
        }
    }

    // ------------------------------------------------------------------ texte

    private String buildText() {
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.GOLD).append(ChatColor.BOLD).append("Classement").append('\n');
        sb.append('\n').append(ChatColor.YELLOW).append(ChatColor.BOLD).append("Victoires KOTH").append('\n');
        appendTop(sb, plugin.stats().getConfigurationSection("wins"), 10);

        sb.append('\n').append(ChatColor.AQUA).append(ChatColor.BOLD).append("Avant-postes").append('\n');
        if (outposts.all().isEmpty()) {
            sb.append(ChatColor.GRAY).append("Aucun avant-poste").append('\n');
        }
        for (OutpostManager.Outpost o : outposts.all()) {
            sb.append(ChatColor.WHITE).append(o.zone.name()).append(ChatColor.GRAY).append(" : ")
                    .append(OutpostManager.ownerLabel(o)).append('\n');
        }

        sb.append('\n').append(ChatColor.AQUA).append(ChatColor.BOLD).append("Captures d'avant-postes").append('\n');
        appendTop(sb, plugin.stats().getConfigurationSection("outpost-captures"), 5);
        return sb.toString().stripTrailing();
    }

    private static void appendTop(StringBuilder sb, ConfigurationSection sec, int limit) {
        if (sec == null || sec.getKeys(false).isEmpty()) {
            sb.append(ChatColor.GRAY).append("Personne pour l'instant").append('\n');
            return;
        }
        List<String> top = sec.getKeys(false).stream()
                .sorted((a, b) -> Integer.compare(sec.getInt(b), sec.getInt(a)))
                .limit(limit)
                .toList();
        for (int i = 0; i < top.size(); i++) {
            String k = top.get(i);
            sb.append(ChatColor.GRAY).append(i + 1).append(". ").append(ChatColor.WHITE).append(k)
                    .append(ChatColor.GRAY).append(" — ").append(ChatColor.GREEN).append(sec.getInt(k)).append('\n');
        }
    }

    // ------------------------------------------------------------------ outils

    private static boolean isLoaded(Location loc) {
        World w = loc.getWorld();
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        // Chunk et entités chargés, sinon on risquerait de créer un doublon
        return w.isChunkLoaded(cx, cz) && w.getChunkAt(cx, cz).isEntitiesLoaded();
    }

    /** TextDisplay du plugin autour de la position (tag PDC ou scoreboard tag). */
    private List<TextDisplay> tagged(Location loc) {
        return loc.getWorld().getNearbyEntities(loc, 3, 3, 3).stream()
                .filter(e -> e instanceof TextDisplay)
                .map(e -> (TextDisplay) e)
                .filter(td -> td.getPersistentDataContainer().has(tag, PersistentDataType.BYTE)
                        || td.getScoreboardTags().contains(SCOREBOARD_TAG))
                .toList();
    }

    private UUID savedUuid() {
        String s = outposts.data().getString(DATA_KEY);
        try {
            return s != null ? UUID.fromString(s) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
