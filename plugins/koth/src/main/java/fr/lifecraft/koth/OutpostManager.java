package fr.lifecraft.koth;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Avant-postes : zones capturables par les factions et tenues dans la durée.
 * Une faction seule dans la zone la capture en "outposts.capture-seconds" secondes ;
 * la faction propriétaire touche les récompenses toutes les "reward-interval-minutes" minutes.
 * Les zones sont dans config.yml (outposts.zones), l'état (propriétaire) dans outposts.yml.
 */
final class OutpostManager implements TabExecutor {

    private static final String PREFIX = ChatColor.AQUA + "" + ChatColor.BOLD + "[Avant-poste] " + ChatColor.RESET;

    /** Verres teintés possibles pour le propriétaire (le blanc est exclu, réservé au neutre). */
    private static final Material[] GLASS_COLORS = {
        Material.ORANGE_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS,
        Material.YELLOW_STAINED_GLASS, Material.LIME_STAINED_GLASS, Material.PINK_STAINED_GLASS,
        Material.GRAY_STAINED_GLASS, Material.LIGHT_GRAY_STAINED_GLASS, Material.CYAN_STAINED_GLASS,
        Material.PURPLE_STAINED_GLASS, Material.BLUE_STAINED_GLASS, Material.BROWN_STAINED_GLASS,
        Material.GREEN_STAINED_GLASS, Material.RED_STAINED_GLASS, Material.BLACK_STAINED_GLASS,
    };

    /** Avant-poste : zone, position du verre et état de capture. */
    static final class Outpost {
        final KothPlugin.Zone zone;
        int[] glass; // {x, y, z} ou null
        String ownerKey;
        String ownerName;
        long capturedAt;
        // Non persisté : progression de la faction qui capture, temps depuis la dernière récompense
        String progressKey;
        String progressName;
        int progress;
        int rewardSeconds;

        Outpost(KothPlugin.Zone zone, int[] glass) {
            this.zone = zone;
            this.glass = glass;
        }
    }

    private final KothPlugin plugin;
    private final Map<String, Outpost> outposts = new LinkedHashMap<>();
    private final File dataFile;
    private YamlConfiguration data;

    OutpostManager(KothPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "outposts.yml");
    }

    void enable() {
        data = YamlConfiguration.loadConfiguration(dataFile);
        loadZones();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        // Une fois les mondes chargés : remet les verres à la couleur du propriétaire
        Bukkit.getScheduler().runTask(plugin, () -> outposts.values().forEach(this::updateGlass));
        plugin.getLogger().info(outposts.size() + " avant-poste(s) chargé(s).");
    }

    Collection<Outpost> all() {
        return outposts.values();
    }

    YamlConfiguration data() {
        return data;
    }

    // ------------------------------------------------------------ chargement

    /** (Re)lit les zones depuis config.yml en gardant l'état (propriétaire, progression). */
    void loadZones() {
        Map<String, Outpost> old = new LinkedHashMap<>(outposts);
        outposts.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("outposts.zones");
        if (sec == null) {
            return;
        }
        for (String name : sec.getKeys(false)) {
            ConfigurationSection z = sec.getConfigurationSection(name);
            if (z == null) {
                continue;
            }
            KothPlugin.Zone zone = new KothPlugin.Zone(name, z.getString("world", "world"),
                    z.getInt("x"), z.getInt("y"), z.getInt("z"), z.getInt("radius", 3), z.getInt("height", 5));
            int[] glass = z.isConfigurationSection("glass")
                    ? new int[] {z.getInt("glass.x"), z.getInt("glass.y"), z.getInt("glass.z")} : null;
            Outpost o = new Outpost(zone, glass);
            String key = name.toLowerCase(Locale.ROOT);
            Outpost prev = old.get(key);
            if (prev != null) {
                o.ownerKey = prev.ownerKey;
                o.ownerName = prev.ownerName;
                o.capturedAt = prev.capturedAt;
                o.progressKey = prev.progressKey;
                o.progressName = prev.progressName;
                o.progress = prev.progress;
                o.rewardSeconds = prev.rewardSeconds;
            } else {
                ConfigurationSection st = data.getConfigurationSection("outposts." + key);
                if (st != null) {
                    o.ownerKey = st.getString("owner-key");
                    o.ownerName = st.getString("owner-name");
                    o.capturedAt = st.getLong("captured-at");
                }
            }
            outposts.put(key, o);
        }
    }

    private void saveZone(Outpost o) {
        KothPlugin.Zone z = o.zone;
        String path = "outposts.zones." + z.name();
        plugin.getConfig().set(path + ".world", z.world());
        plugin.getConfig().set(path + ".x", z.x());
        plugin.getConfig().set(path + ".y", z.y());
        plugin.getConfig().set(path + ".z", z.z());
        plugin.getConfig().set(path + ".radius", z.radius());
        plugin.getConfig().set(path + ".height", z.height());
        if (o.glass != null) {
            plugin.getConfig().set(path + ".glass.x", o.glass[0]);
            plugin.getConfig().set(path + ".glass.y", o.glass[1]);
            plugin.getConfig().set(path + ".glass.z", o.glass[2]);
        }
        plugin.saveConfig();
    }

    /** Enregistre l'état de tous les avant-postes dans outposts.yml. */
    void saveState() {
        data.set("outposts", null);
        for (Map.Entry<String, Outpost> e : outposts.entrySet()) {
            Outpost o = e.getValue();
            if (o.ownerKey == null) {
                continue;
            }
            String path = "outposts." + e.getKey();
            data.set(path + ".owner-key", o.ownerKey);
            data.set(path + ".owner-name", o.ownerName);
            data.set(path + ".captured-at", o.capturedAt);
        }
        saveData();
    }

    void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible d'enregistrer outposts.yml : " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ tick

    private void tick() {
        FactionHook factions = plugin.factions();
        if (factions == null || outposts.isEmpty()) {
            return;
        }
        int capture = Math.max(1, plugin.getConfig().getInt("outposts.capture-seconds", 120));
        int rewardEvery = Math.max(1, plugin.getConfig().getInt("outposts.reward-interval-minutes", 10)) * 60;
        int below = plugin.getConfig().getInt("zone-below", 2);

        // Équipe de chaque joueur en jeu, calculée une seule fois par seconde
        Map<Player, FactionHook.Team> teams = new LinkedHashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            teams.put(p, factions.teamOf(p));
        }

        for (Outpost o : outposts.values()) {
            List<Player> present = new ArrayList<>();
            Map<String, String> inside = new LinkedHashMap<>();
            for (Map.Entry<Player, FactionHook.Team> e : teams.entrySet()) {
                Player p = e.getKey();
                if (!o.zone.contains(p.getLocation(), below)) {
                    continue;
                }
                FactionHook.Team t = e.getValue();
                if (!t.faction()) {
                    actionBar(p, ChatColor.RED + "Il faut une faction pour capturer un avant-poste");
                    continue;
                }
                present.add(p);
                inside.put(t.key(), t.name());
            }

            String status;
            if (inside.size() > 1) {
                // Plusieurs factions (propriétaire compris) : progression gelée
                status = ChatColor.RED + "contesté";
            } else if (inside.size() == 1 && !inside.containsKey(o.ownerKey)) {
                String key = inside.keySet().iterator().next();
                if (!key.equals(o.progressKey)) {
                    // Une autre faction prend le relais : on repart de zéro
                    o.progressKey = key;
                    o.progress = 0;
                }
                o.progressName = inside.get(key);
                o.progress++;
                status = ChatColor.YELLOW + "capture par " + ChatColor.WHITE + o.progressName
                        + ChatColor.YELLOW + " " + Math.min(o.progress, capture) + "/" + capture + " s";
                if (o.progress >= capture) {
                    capture(o, key, o.progressName);
                    status = ChatColor.GREEN + "tenu par " + ChatColor.WHITE + o.ownerName;
                }
            } else {
                // Personne, ou seulement le propriétaire : la progression des attaquants redescend
                if (o.progress > 0) {
                    o.progress--;
                }
                if (o.progress == 0) {
                    o.progressKey = null;
                    o.progressName = null;
                }
                status = o.ownerKey != null ? ChatColor.GREEN + "tenu par " + ChatColor.WHITE + o.ownerName
                        : ChatColor.GRAY + "neutre";
            }
            for (Player p : present) {
                actionBar(p, ChatColor.AQUA + "Avant-poste " + o.zone.name() + " : " + status);
            }

            if (o.ownerKey != null && ++o.rewardSeconds >= rewardEvery) {
                o.rewardSeconds = 0;
                reward(o, factions);
            }
        }
    }

    private void capture(Outpost o, String key, String name) {
        String previous = o.ownerName;
        o.ownerKey = key;
        o.ownerName = name;
        o.capturedAt = System.currentTimeMillis();
        o.progress = 0;
        o.progressKey = null;
        o.progressName = null;
        o.rewardSeconds = 0;
        broadcast(ChatColor.GREEN + "" + ChatColor.BOLD + name + " a capturé l'avant-poste " + o.zone.name()
                + (previous != null ? " (repris à " + previous + ")" : "") + " !");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        String statKey = "outpost-captures." + name;
        plugin.stats().set(statKey, plugin.stats().getInt(statKey) + 1);
        plugin.saveStats();
        saveState();
        updateGlass(o);
    }

    /** Récompenses de la faction propriétaire (commandes console). */
    private void reward(Outpost o, FactionHook factions) {
        List<Player> members = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (o.ownerKey.equals(factions.teamOf(p).key())) {
                members.add(p);
            }
        }
        for (String cmd : plugin.getConfig().getStringList("outposts.rewards")) {
            String line = cmd.replace("{faction}", o.ownerName);
            if (line.contains("{player}")) {
                for (Player p : members) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line.replace("{player}", p.getName()));
                }
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
            }
        }
        for (Player p : members) {
            p.sendMessage(PREFIX + ChatColor.GREEN + "Récompense de l'avant-poste " + o.zone.name() + " reçue.");
        }
    }

    // ------------------------------------------------------------------ verre

    /** Verre teinté de la couleur du propriétaire, ou verre normal si l'avant-poste est neutre. */
    void updateGlass(Outpost o) {
        if (o.glass == null) {
            return;
        }
        World w = Bukkit.getWorld(o.zone.world());
        if (w == null) {
            return;
        }
        Material mat = o.ownerName == null ? Material.GLASS
                : GLASS_COLORS[Math.floorMod(o.ownerName.hashCode(), GLASS_COLORS.length)];
        w.getBlockAt(o.glass[0], o.glass[1], o.glass[2]).setType(mat);
    }

    // ---------------------------------------------------------------- commande

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "list";
        boolean admin = sender.hasPermission("koth.admin");
        switch (sub) {
            case "list" -> {
                if (outposts.isEmpty()) {
                    sender.sendMessage(PREFIX + "Aucun avant-poste pour l'instant.");
                }
                for (Outpost o : outposts.values()) {
                    KothPlugin.Zone z = o.zone;
                    sender.sendMessage(PREFIX + z.name() + " : " + z.world() + " " + z.x() + " " + z.y() + " " + z.z()
                            + ChatColor.GRAY + " — " + ownerLabel(o));
                }
            }
            case "info" -> {
                Outpost o = args.length > 1 ? outposts.get(args[1].toLowerCase(Locale.ROOT)) : null;
                if (o == null) {
                    sender.sendMessage(PREFIX + "Avant-poste inconnu. /" + label + " info <nom>");
                    break;
                }
                KothPlugin.Zone z = o.zone;
                int capture = Math.max(1, plugin.getConfig().getInt("outposts.capture-seconds", 120));
                sender.sendMessage(PREFIX + "Avant-poste " + z.name() + " en " + z.world() + " " + z.x() + " " + z.y() + " " + z.z()
                        + " (rayon " + z.radius() + ")");
                sender.sendMessage(ChatColor.GRAY + " - Propriétaire : " + ownerLabel(o)
                        + (o.ownerKey != null ? ChatColor.GRAY + " depuis le "
                                + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(o.capturedAt)) : ""));
                if (o.progressKey != null) {
                    sender.sendMessage(ChatColor.GRAY + " - Capture en cours : " + ChatColor.WHITE + o.progressName
                            + ChatColor.GRAY + " " + o.progress + "/" + capture + " s");
                }
                sender.sendMessage(ChatColor.GRAY + " - Récompenses toutes les "
                        + plugin.getConfig().getInt("outposts.reward-interval-minutes", 10) + " min");
            }
            case "create" -> {
                if (!admin) {
                    return noPerm(sender);
                }
                create(sender, label, args);
            }
            case "glass" -> {
                if (!admin) {
                    return noPerm(sender);
                }
                Outpost o = args.length > 4 ? outposts.get(args[1].toLowerCase(Locale.ROOT)) : null;
                if (o == null) {
                    sender.sendMessage(PREFIX + "/" + label + " glass <nom> <x> <y> <z>");
                    break;
                }
                Integer x = parseCoord(args[2], sender, 0);
                Integer y = parseCoord(args[3], sender, 1);
                Integer z = parseCoord(args[4], sender, 2);
                if (x == null || y == null || z == null) {
                    sender.sendMessage(PREFIX + "Coordonnées invalides.");
                    break;
                }
                o.glass = new int[] {x, y, z};
                saveZone(o);
                updateGlass(o);
                sender.sendMessage(PREFIX + "Verre de l'avant-poste " + o.zone.name() + " placé en " + x + " " + y + " " + z + ".");
            }
            case "delete" -> {
                if (!admin) {
                    return noPerm(sender);
                }
                Outpost o = args.length > 1 ? outposts.remove(args[1].toLowerCase(Locale.ROOT)) : null;
                if (o == null) {
                    sender.sendMessage(PREFIX + "Avant-poste inconnu.");
                    break;
                }
                plugin.getConfig().set("outposts.zones." + o.zone.name(), null);
                plugin.saveConfig();
                saveState();
                sender.sendMessage(PREFIX + "Avant-poste " + o.zone.name() + " supprimé.");
            }
            case "reset" -> {
                if (!admin) {
                    return noPerm(sender);
                }
                Outpost o = args.length > 1 ? outposts.get(args[1].toLowerCase(Locale.ROOT)) : null;
                if (o == null) {
                    sender.sendMessage(PREFIX + "Avant-poste inconnu.");
                    break;
                }
                o.ownerKey = null;
                o.ownerName = null;
                o.capturedAt = 0;
                o.progressKey = null;
                o.progressName = null;
                o.progress = 0;
                o.rewardSeconds = 0;
                saveState();
                updateGlass(o);
                broadcast(ChatColor.YELLOW + "L'avant-poste " + o.zone.name() + " redevient neutre.");
            }
            case "reload" -> {
                if (!admin) {
                    return noPerm(sender);
                }
                plugin.reloadAll();
                sender.sendMessage(PREFIX + "Configuration rechargée (" + outposts.size() + " avant-poste(s)).");
            }
            default -> sender.sendMessage(PREFIX + "/" + label + " <list|info"
                    + (admin ? "|create|glass|delete|reset|reload" : "") + ">");
        }
        return true;
    }

    private void create(CommandSender sender, String label, String[] args) {
        KothPlugin.Zone z;
        if (args.length >= 5) {
            // Console ou jeu : /ap create <nom> <x> <y> <z> [rayon] [hauteur] [monde]
            Integer x = parseCoord(args[2], sender, 0);
            Integer y = parseCoord(args[3], sender, 1);
            Integer zz = parseCoord(args[4], sender, 2);
            if (x == null || y == null || zz == null) {
                sender.sendMessage(PREFIX + "Coordonnées invalides.");
                return;
            }
            int radius = args.length > 5 ? KothPlugin.parseInt(args[5], 3) : 3;
            int height = args.length > 6 ? KothPlugin.parseInt(args[6], 5) : 5;
            String world = args.length > 7 ? args[7] : "world";
            if (Bukkit.getWorld(world) == null) {
                sender.sendMessage(PREFIX + "Monde inconnu : " + world);
                return;
            }
            z = new KothPlugin.Zone(args[1], world, x, y, zz, radius, height);
        } else if (sender instanceof Player p && args.length >= 2) {
            // En jeu : centré sur le joueur
            int radius = args.length > 2 ? KothPlugin.parseInt(args[2], 3) : 3;
            int height = args.length > 3 ? KothPlugin.parseInt(args[3], 5) : 5;
            Location l = p.getLocation();
            z = new KothPlugin.Zone(args[1], l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ(), radius, height);
        } else {
            sender.sendMessage(PREFIX + "En jeu : /" + label + " create <nom> [rayon=3] [hauteur=5], debout au centre.");
            sender.sendMessage(PREFIX + "Console : /" + label + " create <nom> <x> <y> <z> [rayon=3] [hauteur=5] [monde=world]");
            return;
        }
        String key = z.name().toLowerCase(Locale.ROOT);
        Outpost prev = outposts.get(key);
        if (prev != null) {
            // Recréation d'une zone existante : on enlève l'ancienne entrée (casse du nom comprise)
            plugin.getConfig().set("outposts.zones." + prev.zone.name(), null);
        }
        Outpost o = new Outpost(z, prev != null ? prev.glass : null);
        if (prev != null) {
            copyState(prev, o);
        }
        outposts.put(key, o);
        saveZone(o);
        sender.sendMessage(PREFIX + "Avant-poste " + z.name() + " créé en " + z.x() + " " + z.y() + " " + z.z()
                + " (zone de " + (z.radius() * 2 + 1) + "x" + (z.radius() * 2 + 1) + ").");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("list", "info"));
            if (sender.hasPermission("koth.admin")) {
                subs.addAll(List.of("create", "glass", "delete", "reset", "reload"));
            }
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && List.of("info", "glass", "delete", "reset").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (Outpost o : outposts.values()) {
                if (o.zone.name().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(o.zone.name());
                }
            }
        } else if (args.length >= 3 && args.length <= 5 && sender instanceof Player p
                && (args[0].equalsIgnoreCase("glass") || args[0].equalsIgnoreCase("create"))) {
            // Propose la position du bloc visé (ou du joueur)
            var target = p.getTargetBlockExact(6);
            Location l = target != null ? target.getLocation() : p.getLocation();
            int[] c = {l.getBlockX(), l.getBlockY(), l.getBlockZ()};
            out.add(String.valueOf(c[args.length - 3]));
        } else if (args.length == 8 && args[0].equalsIgnoreCase("create")) {
            for (World w : Bukkit.getWorlds()) {
                out.add(w.getName());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ outils

    private static void copyState(Outpost from, Outpost to) {
        to.ownerKey = from.ownerKey;
        to.ownerName = from.ownerName;
        to.capturedAt = from.capturedAt;
        to.progressKey = from.progressKey;
        to.progressName = from.progressName;
        to.progress = from.progress;
        to.rewardSeconds = from.rewardSeconds;
    }

    static String ownerLabel(Outpost o) {
        return o.ownerName != null ? ChatColor.GREEN + "tenu par " + ChatColor.WHITE + o.ownerName : ChatColor.GRAY + "neutre";
    }

    /** Coordonnée entière ; "~" ou "~n" relatif à la position du joueur (axe 0=x, 1=y, 2=z). */
    private static Integer parseCoord(String s, CommandSender sender, int axis) {
        int base = 0;
        if (s.startsWith("~")) {
            if (!(sender instanceof Player p)) {
                return null;
            }
            Location l = p.getLocation();
            base = axis == 0 ? l.getBlockX() : axis == 1 ? l.getBlockY() : l.getBlockZ();
            s = s.substring(1);
            if (s.isEmpty()) {
                return base;
            }
        }
        try {
            return base + Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void actionBar(Player p, String msg) {
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(msg));
    }

    private boolean noPerm(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.RED + "Tu n'as pas la permission.");
        return true;
    }

    private static void broadcast(String msg) {
        Bukkit.broadcastMessage(PREFIX + msg);
    }
}
