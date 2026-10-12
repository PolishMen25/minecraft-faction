package fr.lifecraft.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

/**
 * Spawn propre : aucune créature (sauf les PNJ marchands, posés par commande et tagués),
 * aucun objet au sol, pas de dégâts de chute. Un objet jeté reste dans l'inventaire ; à la mort,
 * on garde tout.
 * Les zones exclues (box de minage) laissent tomber les objets des blocs cassés ; on n'y jette
 * toujours rien et les créatures y restent interdites.
 */
final class SpawnGuard implements Listener {

    /** Seules les apparitions par commande ou par plugin sont permises (PNJ du marché). */
    private static final Set<CreatureSpawnEvent.SpawnReason> ALLOWED =
        Set.of(CreatureSpawnEvent.SpawnReason.COMMAND, CreatureSpawnEvent.SpawnReason.CUSTOM);

    private final LifeCore plugin;
    private String world;
    private BoundingBox box;
    private final List<BoundingBox> excluded = new ArrayList<>();
    private String keepTag;
    private String dropMessage;
    private boolean noFallDamage;
    private BukkitTask sweepTask;

    SpawnGuard(LifeCore plugin) {
        this.plugin = plugin;
    }

    void start() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("spawn-guard");
        if (c == null || !c.getBoolean("enabled", true)) {
            box = null;
            return;
        }
        world = c.getString("world", "world");
        box = new BoundingBox(c.getInt("min-x"), -64, c.getInt("min-z"),
            c.getInt("max-x") + 1, 320, c.getInt("max-z") + 1);
        excluded.clear();
        for (String ex : c.getStringList("exclude")) {
            String[] v = ex.split(",");
            if (v.length != 6) {
                plugin.getLogger().warning("spawn-guard.exclude : « " + ex + " » ignoré (attendu x1,y1,z1,x2,y2,z2)");
                continue;
            }
            int[] n = new int[6];
            for (int i = 0; i < 6; i++) {
                n[i] = Integer.parseInt(v[i].trim());
            }
            excluded.add(BoundingBox.of(new org.bukkit.util.Vector(n[0], n[1], n[2]),
                new org.bukkit.util.Vector(n[3], n[4], n[5])).expand(0, 0, 0, 1, 1, 1));
        }
        keepTag = c.getString("keep-tag", "lifecraft_pnj");
        dropMessage = c.getString("drop-message", "");
        noFallDamage = c.getBoolean("no-fall-damage", true);
        long ticks = Math.max(1, c.getLong("sweep-seconds", 5)) * 20;
        sweepTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, ticks, ticks);
    }

    void stop() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
    }

    private boolean inside(Location l) {
        return box != null && l.getWorld() != null && l.getWorld().getName().equals(world)
            && box.contains(l.getX(), l.getY(), l.getZ());
    }

    /** Zones où les objets restent permis (box de minage : les minerais doivent tomber). */
    private boolean isExcluded(double x, double y, double z) {
        for (BoundingBox b : excluded) {
            if (b.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /** Créatures entrées dans le spawn et objets lancés depuis l'extérieur : retirés. */
    private void sweep() {
        World w = Bukkit.getWorld(world);
        if (w == null || box == null) {
            return;
        }
        for (Entity e : w.getNearbyEntities(box)) {
            if (e instanceof Item && isExcluded(e.getX(), e.getY(), e.getZ())) {
                continue;
            }
            if (e instanceof Item || (e instanceof LivingEntity && !(e instanceof Player)
                    && !(e instanceof ArmorStand) && !e.getScoreboardTags().contains(keepTag))) {
                e.remove();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        if (!ALLOWED.contains(e.getSpawnReason()) && !(e.getEntity() instanceof ArmorStand)
                && inside(e.getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (inside(e.getPlayer().getLocation())) {
            e.setCancelled(true);
            if (!dropMessage.isBlank()) {
                e.getPlayer().sendActionBar(Text.color(dropMessage));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent e) {
        Location l = e.getLocation();
        if (inside(l) && !isExcluded(l.getX(), l.getY(), l.getZ())) {
            e.setCancelled(true);
        }
    }

    /** La place est ~17 blocs au-dessus de la forêt, sans escalier : on en descend en sautant. */
    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (noFallDamage && e.getCause() == EntityDamageEvent.DamageCause.FALL
                && e.getEntity() instanceof Player p && inside(p.getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        if (inside(e.getEntity().getLocation())) {
            e.setKeepInventory(true);
            e.getDrops().clear();
            e.setKeepLevel(true);
            e.setDroppedExp(0);
        }
    }
}
