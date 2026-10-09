package fr.lifecraft.koth;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class KothPlugin extends JavaPlugin {

    private static final String PREFIX = ChatColor.GOLD + "" + ChatColor.BOLD + "[KOTH] " + ChatColor.RESET;
    private static final DateTimeFormatter MINUTE_KEY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Zone de KOTH : cube centré sur un bloc. */
    record Zone(String name, String world, int x, int y, int z, int radius, int height) {
        boolean contains(Location l, int below) {
            if (l.getWorld() == null || !l.getWorld().getName().equals(world)) {
                return false;
            }
            int bx = l.getBlockX();
            int by = l.getBlockY();
            int bz = l.getBlockZ();
            return Math.abs(bx - x) <= radius && Math.abs(bz - z) <= radius && by >= y - below && by <= y + height;
        }
    }

    /** Event en cours. */
    private static final class Active {
        final Zone zone;
        final long startedAt = System.currentTimeMillis();
        final BossBar bar;
        String capKey;
        String capName;
        int secondsLeft;

        Active(Zone zone, BossBar bar) {
            this.zone = zone;
            this.bar = bar;
        }
    }

    private final Map<String, Zone> zones = new LinkedHashMap<>();
    private final Random random = new Random();
    private FactionHook factions;
    private YamlConfiguration stats;
    private File statsFile;
    private Active active;
    private BukkitTask tickTask;
    private String lastScheduleKey = "";
    private String lastWarnKey = "";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadZones();
        statsFile = new File(getDataFolder(), "stats.yml");
        stats = YamlConfiguration.loadConfiguration(statsFile);
        // Après le chargement de tous les plugins, pour trouver FactionsUUID
        Bukkit.getScheduler().runTask(this, () -> factions = new FactionHook(getLogger()));
        Bukkit.getScheduler().runTaskTimer(this, this::checkSchedule, 20L * 10, 20L * 20);
        getLogger().info(zones.size() + " KOTH chargée(s).");
    }

    @Override
    public void onDisable() {
        if (active != null) {
            stop(false);
        }
    }

    // ------------------------------------------------------------------ zones

    private void loadZones() {
        zones.clear();
        ConfigurationSection sec = getConfig().getConfigurationSection("zones");
        if (sec == null) {
            return;
        }
        for (String name : sec.getKeys(false)) {
            ConfigurationSection z = sec.getConfigurationSection(name);
            if (z == null) {
                continue;
            }
            zones.put(name.toLowerCase(Locale.ROOT), new Zone(name, z.getString("world", "world"),
                    z.getInt("x"), z.getInt("y"), z.getInt("z"), z.getInt("radius", 3), z.getInt("height", 5)));
        }
    }

    private void saveZone(Zone z) {
        String path = "zones." + z.name();
        getConfig().set(path + ".world", z.world());
        getConfig().set(path + ".x", z.x());
        getConfig().set(path + ".y", z.y());
        getConfig().set(path + ".z", z.z());
        getConfig().set(path + ".radius", z.radius());
        getConfig().set(path + ".height", z.height());
        saveConfig();
    }

    // ---------------------------------------------------------------- planning

    private void checkSchedule() {
        ConfigurationSection s = getConfig().getConfigurationSection("schedule");
        if (s == null || !s.getBoolean("enabled", true) || zones.isEmpty()) {
            return;
        }
        ZonedDateTime now;
        try {
            now = ZonedDateTime.now(ZoneId.of(s.getString("timezone", "Europe/Paris")));
        } catch (RuntimeException e) {
            now = ZonedDateTime.now();
        }
        List<String> days = s.getStringList("days");
        int warn = s.getInt("warn-minutes", 5);
        for (String time : s.getStringList("times")) {
            String[] hm = time.trim().split(":");
            if (hm.length != 2) {
                continue;
            }
            ZonedDateTime at;
            try {
                at = now.withHour(Integer.parseInt(hm[0])).withMinute(Integer.parseInt(hm[1])).withSecond(0).withNano(0);
            } catch (RuntimeException e) {
                continue;
            }
            if (!dayAllowed(days, at.getDayOfWeek())) {
                continue;
            }
            String key = at.format(MINUTE_KEY);
            String nowKey = now.format(MINUTE_KEY);
            if (warn > 0 && at.minusMinutes(warn).format(MINUTE_KEY).equals(nowKey) && !key.equals(lastWarnKey)) {
                lastWarnKey = key;
                broadcast(ChatColor.YELLOW + "Une KOTH commence dans " + warn + " minutes !");
            }
            if (key.equals(nowKey) && !key.equals(lastScheduleKey) && active == null) {
                lastScheduleKey = key;
                Zone z = pickZone(s.getString("koth", "random"));
                if (z != null) {
                    start(z);
                }
            }
        }
    }

    private static boolean dayAllowed(List<String> days, DayOfWeek d) {
        return days.isEmpty() || days.stream().anyMatch(x -> x.equalsIgnoreCase(d.name()));
    }

    private Zone pickZone(String name) {
        if (zones.isEmpty()) {
            return null;
        }
        if (name != null && !name.equalsIgnoreCase("random") && zones.containsKey(name.toLowerCase(Locale.ROOT))) {
            return zones.get(name.toLowerCase(Locale.ROOT));
        }
        List<Zone> all = new ArrayList<>(zones.values());
        return all.get(random.nextInt(all.size()));
    }

    // ------------------------------------------------------------------- event

    private void start(Zone z) {
        BossBar bar = Bukkit.createBossBar(ChatColor.GOLD + "KOTH " + z.name() + " : libre", BarColor.YELLOW, BarStyle.SEGMENTED_10);
        bar.setProgress(0);
        active = new Active(z, bar);
        broadcast(ChatColor.GREEN + "La KOTH " + ChatColor.WHITE + z.name() + ChatColor.GREEN + " commence en "
                + ChatColor.WHITE + z.x() + " " + z.y() + " " + z.z() + ChatColor.GREEN + " ! Tenez la zone "
                + formatTime(getConfig().getInt("capture-seconds", 300)) + " pour la capturer.");
        tickTask = Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
    }

    private void stop(boolean announce) {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (active != null) {
            active.bar.removeAll();
            if (announce) {
                broadcast(ChatColor.RED + "La KOTH " + active.zone.name() + " est terminée sans vainqueur.");
            }
        }
        active = null;
    }

    private void tick() {
        Active a = active;
        if (a == null) {
            return;
        }
        long maxMs = getConfig().getLong("max-duration-minutes", 30) * 60_000L;
        if (System.currentTimeMillis() - a.startedAt > maxMs) {
            stop(true);
            return;
        }
        int capture = Math.max(1, getConfig().getInt("capture-seconds", 300));
        int below = getConfig().getInt("zone-below", 2);

        // Équipes présentes dans la zone
        Map<String, List<Player>> inside = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!a.bar.getPlayers().contains(p)) {
                a.bar.addPlayer(p);
            }
            if (p.isDead() || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (!a.zone.contains(p.getLocation(), below)) {
                continue;
            }
            FactionHook.Team t = factions != null ? factions.teamOf(p)
                    : new FactionHook.Team("p:" + p.getUniqueId(), p.getName(), false);
            inside.computeIfAbsent(t.key(), k -> new ArrayList<>()).add(p);
            names.put(t.key(), t.name());
        }

        if (a.capKey != null && !inside.containsKey(a.capKey)) {
            broadcast(ChatColor.RED + a.capName + " a perdu le contrôle de la KOTH !");
            a.capKey = null;
            a.capName = null;
        }
        if (a.capKey == null && inside.size() == 1) {
            a.capKey = inside.keySet().iterator().next();
            a.capName = names.get(a.capKey);
            a.secondsLeft = capture;
            broadcast(ChatColor.YELLOW + a.capName + " commence à capturer la KOTH " + a.zone.name() + " !");
        }

        if (a.capKey == null) {
            a.bar.setTitle(ChatColor.GOLD + "KOTH " + a.zone.name() + " : " + (inside.isEmpty() ? "libre" : "contestée"));
            a.bar.setColor(inside.isEmpty() ? BarColor.YELLOW : BarColor.RED);
            a.bar.setProgress(0);
            return;
        }

        boolean contested = inside.size() > 1;
        if (!contested) {
            a.secondsLeft--;
            if (a.secondsLeft > 0 && a.secondsLeft % 60 == 0) {
                broadcast(ChatColor.YELLOW + a.capName + " capture la KOTH : encore " + formatTime(a.secondsLeft) + ".");
            }
        }
        a.bar.setTitle(ChatColor.GOLD + "KOTH " + a.zone.name() + " : " + ChatColor.WHITE + a.capName
                + (contested ? ChatColor.RED + " (contestée)" : ChatColor.GRAY + " — " + formatTime(Math.max(0, a.secondsLeft))));
        a.bar.setColor(contested ? BarColor.RED : BarColor.GREEN);
        a.bar.setProgress(Math.min(1.0, Math.max(0.0, 1.0 - (double) a.secondsLeft / capture)));

        if (a.secondsLeft <= 0) {
            win(a, inside.get(a.capKey));
        }
    }

    private void win(Active a, List<Player> players) {
        Player winner = players.get(0);
        FactionHook.Team team = factions != null ? factions.teamOf(winner)
                : new FactionHook.Team("p:" + winner.getUniqueId(), winner.getName(), false);
        stop(false);
        broadcast(ChatColor.GREEN + "" + ChatColor.BOLD + team.name() + " a capturé la KOTH " + a.zone.name() + " !");

        String statKey = "wins." + team.name();
        stats.set(statKey, stats.getInt(statKey) + 1);
        try {
            stats.save(statsFile);
        } catch (IOException e) {
            getLogger().warning("Impossible d'enregistrer stats.yml : " + e.getMessage());
        }

        for (String cmd : getConfig().getStringList("rewards.commands")) {
            if (cmd.contains("{faction}") && !team.faction()) {
                continue;
            }
            String line = cmd.replace("{player}", winner.getName()).replace("{faction}", team.name());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
        }
        if (getConfig().getBoolean("rewards.loot-chest", true)) {
            spawnLoot(a.zone);
        }
    }

    private void spawnLoot(Zone z) {
        World w = Bukkit.getWorld(z.world());
        if (w == null) {
            return;
        }
        Block b = w.getBlockAt(z.x(), z.y() + 1, z.z());
        b.setType(Material.CHEST);
        if (!(b.getState() instanceof Chest chest)) {
            return;
        }
        for (String spec : getConfig().getStringList("rewards.loot")) {
            ItemStack item = parseItem(spec);
            if (item != null) {
                chest.getBlockInventory().addItem(item);
            }
        }
        broadcast(ChatColor.AQUA + "Un coffre de récompenses est apparu au centre de la KOTH !");
    }

    private ItemStack parseItem(String spec) {
        String[] parts = spec.split(":");
        Material mat = Material.matchMaterial(parts[0].trim());
        if (mat == null) {
            getLogger().warning("Objet inconnu dans le loot : " + spec);
            return null;
        }
        int amount = 1;
        if (parts.length > 1) {
            try {
                amount = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ignored) {
                // quantité invalide : 1
            }
        }
        ItemStack item = new ItemStack(mat, Math.max(1, amount));
        if (parts.length > 2) {
            for (String ench : parts[2].split(",")) {
                String[] kv = ench.split("=");
                Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(kv[0].trim().toLowerCase(Locale.ROOT)));
                if (e == null) {
                    getLogger().warning("Enchantement inconnu : " + kv[0]);
                    continue;
                }
                int lvl = 1;
                if (kv.length > 1) {
                    try {
                        lvl = Integer.parseInt(kv[1].trim());
                    } catch (NumberFormatException ignored) {
                        // niveau invalide : 1
                    }
                }
                item.addUnsafeEnchantment(e, lvl);
            }
        }
        return item;
    }

    // ---------------------------------------------------------------- commande

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "info";
        boolean admin = sender.hasPermission("koth.admin");
        switch (sub) {
            case "info" -> {
                if (active == null) {
                    sender.sendMessage(PREFIX + "Aucune KOTH en cours. Planning : "
                            + String.join(", ", getConfig().getStringList("schedule.times")));
                } else {
                    Zone z = active.zone;
                    sender.sendMessage(PREFIX + "KOTH " + z.name() + " en " + z.x() + " " + z.y() + " " + z.z()
                            + (active.capKey == null ? " — libre" : " — " + active.capName + ", " + formatTime(active.secondsLeft)));
                }
            }
            case "list" -> {
                if (zones.isEmpty()) {
                    sender.sendMessage(PREFIX + "Aucune KOTH. Crée-en une avec /koth create <nom>.");
                }
                for (Zone z : zones.values()) {
                    sender.sendMessage(PREFIX + z.name() + " : " + z.world() + " " + z.x() + " " + z.y() + " " + z.z()
                            + " (rayon " + z.radius() + ")");
                }
            }
            case "top" -> {
                ConfigurationSection w = stats.getConfigurationSection("wins");
                if (w == null || w.getKeys(false).isEmpty()) {
                    sender.sendMessage(PREFIX + "Aucune KOTH capturée pour l'instant.");
                    break;
                }
                sender.sendMessage(PREFIX + "Classement des captures :");
                w.getKeys(false).stream()
                        .sorted((x, y) -> Integer.compare(w.getInt(y), w.getInt(x)))
                        .limit(10)
                        .forEach(k -> sender.sendMessage(ChatColor.GRAY + " - " + ChatColor.WHITE + k + ChatColor.GRAY + " : " + w.getInt(k)));
            }
            case "start" -> {
                if (!admin) {
                    return noPerm(sender);
                }
                if (active != null) {
                    sender.sendMessage(PREFIX + "Une KOTH est déjà en cours.");
                    break;
                }
                Zone z = pickZone(args.length > 1 ? args[1] : "random");
                if (z == null) {
                    sender.sendMessage(PREFIX + "Aucune KOTH définie.");
                    break;
                }
                start(z);
            }
            case "stop" -> {
                if (!admin) {
                    return noPerm(sender);
                }
                if (active == null) {
                    sender.sendMessage(PREFIX + "Aucune KOTH en cours.");
                } else {
                    stop(true);
                }
            }
            case "create" -> {
                if (!admin) {
                    return noPerm(sender);
                }
                if (!(sender instanceof Player p) || args.length < 2) {
                    sender.sendMessage(PREFIX + "En jeu : /koth create <nom> [rayon=3] [hauteur=5], debout au centre.");
                    break;
                }
                int radius = args.length > 2 ? parseInt(args[2], 3) : 3;
                int height = args.length > 3 ? parseInt(args[3], 5) : 5;
                Location l = p.getLocation();
                Zone z = new Zone(args[1], l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ(), radius, height);
                zones.put(z.name().toLowerCase(Locale.ROOT), z);
                saveZone(z);
                sender.sendMessage(PREFIX + "KOTH " + z.name() + " créée en " + z.x() + " " + z.y() + " " + z.z()
                        + " (zone de " + (radius * 2 + 1) + "x" + (radius * 2 + 1) + ").");
            }
            case "delete" -> {
                if (!admin) {
                    return noPerm(sender);
                }
                if (args.length < 2 || zones.remove(args[1].toLowerCase(Locale.ROOT)) == null) {
                    sender.sendMessage(PREFIX + "KOTH inconnue.");
                    break;
                }
                getConfig().set("zones." + args[1], null);
                saveConfig();
                sender.sendMessage(PREFIX + "KOTH " + args[1] + " supprimée.");
            }
            case "reload" -> {
                if (!admin) {
                    return noPerm(sender);
                }
                reloadConfig();
                loadZones();
                sender.sendMessage(PREFIX + "Configuration rechargée (" + zones.size() + " KOTH).");
            }
            default -> sender.sendMessage(PREFIX + "/koth <info|list|top" + (admin ? "|start|stop|create|delete|reload" : "") + ">");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("info", "list", "top"));
            if (sender.hasPermission("koth.admin")) {
                subs.addAll(List.of("start", "stop", "create", "delete", "reload"));
            }
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("delete"))) {
            for (Zone z : zones.values()) {
                out.add(z.name());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ outils

    private boolean noPerm(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.RED + "Tu n'as pas la permission.");
        return true;
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String formatTime(int seconds) {
        return seconds >= 60 ? (seconds / 60) + " min" + (seconds % 60 > 0 ? " " + (seconds % 60) + " s" : "") : seconds + " s";
    }

    private static void broadcast(String msg) {
        Bukkit.broadcastMessage(PREFIX + msg);
    }
}
