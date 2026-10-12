package fr.lifecraft.koth;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Zones libres : cuboïdes qui restent en safezone FactionsUUID (donc non claimables) mais où
 * certaines actions sont autorisées : casser des blocs, poser certains blocs, se battre entre joueurs.
 *
 * FactionsUUID 4.7.2 bloque tout cela à la priorité NORMAL avec ignoreCancelled=true
 * (ListenBlock : BlockBreak/BlockPlace/BlockDamage ; ListenDamage#onEntityDamage). Nos écouteurs
 * passent en HIGHEST avec ignoreCancelled=false : ils s'exécutent après et lèvent l'annulation.
 */
final class FreeZones implements Listener {

    /** Cuboïde libre avec ses autorisations. */
    record Free(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2,
                boolean breakBlocks, Set<Material> breakOnly, Set<Material> place, boolean pvp) {
        boolean contains(Location l) {
            if (l == null || l.getWorld() == null || !l.getWorld().getName().equals(world)) {
                return false;
            }
            int x = l.getBlockX();
            int y = l.getBlockY();
            int z = l.getBlockZ();
            return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
        }
    }

    /** Blocs jamais dé-protégés, même dans une zone « break: true ». */
    private static final Set<Material> NEVER = EnumSet.of(Material.REINFORCED_DEEPSLATE, Material.BEDROCK, Material.BARRIER);

    private final KothPlugin plugin;
    private final Map<String, Free> zones = new LinkedHashMap<>();
    private boolean powerHooked;

    FreeZones(KothPlugin plugin) {
        this.plugin = plugin;
    }

    void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        hookPowerLoss();
        loadZones();
    }

    Map<String, Free> all() {
        return zones;
    }

    /** (Re)charge la section free-zones de config.yml. */
    void loadZones() {
        zones.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("free-zones");
        if (sec == null) {
            return;
        }
        for (String name : sec.getKeys(false)) {
            ConfigurationSection z = sec.getConfigurationSection(name);
            if (z == null) {
                continue;
            }
            Set<Material> place = materials(name, "place", z.getStringList("place"));
            Set<Material> breakOnly = materials(name, "break-only", z.getStringList("break-only"));
            zones.put(name.toLowerCase(Locale.ROOT), new Free(name, z.getString("world", "world"),
                    Math.min(z.getInt("x1"), z.getInt("x2")), Math.min(z.getInt("y1"), z.getInt("y2")),
                    Math.min(z.getInt("z1"), z.getInt("z2")), Math.max(z.getInt("x1"), z.getInt("x2")),
                    Math.max(z.getInt("y1"), z.getInt("y2")), Math.max(z.getInt("z1"), z.getInt("z2")),
                    z.getBoolean("break", false), breakOnly, place, z.getBoolean("pvp", false)));
        }
        plugin.getLogger().info(zones.size() + " zone(s) libre(s) chargée(s).");
    }

    private Set<Material> materials(String zone, String key, List<String> names) {
        Set<Material> out = EnumSet.noneOf(Material.class);
        for (String m : names) {
            Material mat = Material.matchMaterial(m.trim());
            if (mat == null) {
                plugin.getLogger().warning("Zone libre " + zone + " : bloc inconnu dans " + key + " : " + m);
            } else {
                out.add(mat);
            }
        }
        return out;
    }

    /** Description d'une zone pour /koth zones. */
    static String describe(Free f) {
        return f.name() + " : " + f.world() + " " + f.x1() + " " + f.y1() + " " + f.z1() + " -> "
                + f.x2() + " " + f.y2() + " " + f.z2()
                + " | casse " + (!f.breakBlocks() ? "non" : f.breakOnly().isEmpty() ? "tout" : f.breakOnly().size() + " bloc(s)")
                + " | pose " + (f.place().isEmpty() ? "aucune" : f.place().toString())
                + " | pvp " + (f.pvp() ? "oui" : "non");
    }

    private Free at(Location l, java.util.function.Predicate<Free> rule) {
        for (Free f : zones.values()) {
            if (rule.test(f) && f.contains(l)) {
                return f;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ blocs

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent e) {
        if (e.isCancelled() && breakAllowed(e.getBlock().getLocation(), e.getBlock().getType())) {
            e.setCancelled(false);
        }
    }

    /** FactionsUUID annule aussi BlockDamageEvent (début de casse) : nécessaire pour la casse instantanée. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(BlockDamageEvent e) {
        if (e.isCancelled() && breakAllowed(e.getBlock().getLocation(), e.getBlock().getType())) {
            e.setCancelled(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlace(BlockPlaceEvent e) {
        if (!e.isCancelled()) {
            return;
        }
        Material m = e.getBlockPlaced().getType();
        if (!NEVER.contains(m) && at(e.getBlockPlaced().getLocation(), f -> f.place().contains(m)) != null) {
            e.setCancelled(false);
        }
    }

    private boolean breakAllowed(Location l, Material m) {
        return !NEVER.contains(m) && at(l, f -> f.breakBlocks() && (f.breakOnly().isEmpty() || f.breakOnly().contains(m))) != null;
    }

    // -------------------------------------------------------------------- pvp

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!e.isCancelled() || !(e.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = shooter(e.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (at(victim.getLocation(), Free::pvp) != null && at(attacker.getLocation(), Free::pvp) != null) {
            e.setCancelled(false);
        }
    }

    private static Player shooter(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    // ------------------------------------------------------------------ power

    /**
     * FactionsUUID ne retire pas de cas « safezone » dans PowerControl#onDeath : un mort en safezone
     * perd du power. On annule donc PowerLossEvent pour une mort dans une zone pvp. Par réflexion,
     * comme FactionHook, pour ne pas dépendre de l'API à la compilation.
     */
    @SuppressWarnings("unchecked")
    private void hookPowerLoss() {
        try {
            Class<? extends Event> cls = (Class<? extends Event>) Class.forName("dev.kitteh.factions.event.PowerLossEvent");
            var getFPlayer = cls.getMethod("getFPlayer");
            var asPlayer = getFPlayer.getReturnType().getMethod("asPlayer");
            Bukkit.getPluginManager().registerEvent(cls, this, EventPriority.HIGHEST, (listener, event) -> {
                if (!cls.isInstance(event)) {
                    return;
                }
                try {
                    Object fp = getFPlayer.invoke(event);
                    Player p = fp == null ? null : (Player) asPlayer.invoke(fp);
                    if (p != null && at(p.getLocation(), Free::pvp) != null) {
                        ((Cancellable) event).setCancelled(true);
                    }
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    plugin.getLogger().warning("PowerLossEvent : " + ex);
                }
            }, plugin, false);
            powerHooked = true;
            plugin.getLogger().info("Zones libres : pas de perte de power dans les zones pvp.");
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().warning("PowerLossEvent introuvable : la mort en zone pvp coûtera du power.");
        }
    }

    boolean powerHooked() {
        return powerHooked;
    }

    List<String> descriptions() {
        List<String> out = new ArrayList<>();
        for (Free f : zones.values()) {
            out.add(describe(f));
        }
        return out;
    }
}
