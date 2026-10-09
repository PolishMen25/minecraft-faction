package fr.lifecraft.items;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Minerai custom : chance de lâcher un Éclat de faille en minant en profondeur.
 * Anti-farm : les blocs de minerai posés par un joueur sont mémorisés dans les données du chunk
 * (PersistentDataContainer) et ne lâchent jamais d'éclat quand on les casse.
 */
final class OreListener implements Listener {

    private final LifeItemsPlugin plugin;
    private final NamespacedKey placedKey;

    OreListener(LifeItemsPlugin plugin) {
        this.plugin = plugin;
        this.placedKey = new NamespacedKey(plugin, "placed");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Settings s = plugin.settings();
        Block block = event.getBlockPlaced();
        if (s.oreBlocks.contains(block.getType()) && s.oreWorlds.contains(block.getWorld().getName())
                && block.getY() < s.oreMaxY) {
            setPlaced(block, true);
        }
    }

    /** Position du bloc dans son chunk, sur un entier : y (décalé), x et z locaux. */
    private static int pack(Block b) {
        return ((b.getY() + 4096) << 8) | ((b.getX() & 15) << 4) | (b.getZ() & 15);
    }

    /** Ajoute ou retire le bloc de la liste des blocs posés ; renvoie vrai s'il y était. */
    private boolean setPlaced(Block b, boolean placed) {
        Chunk chunk = b.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        int[] list = pdc.getOrDefault(placedKey, PersistentDataType.INTEGER_ARRAY, new int[0]);
        int key = pack(b);
        int idx = -1;
        for (int i = 0; i < list.length; i++) {
            if (list[i] == key) {
                idx = i;
                break;
            }
        }
        if (placed && idx < 0) {
            int[] next = Arrays.copyOf(list, list.length + 1);
            next[list.length] = key;
            pdc.set(placedKey, PersistentDataType.INTEGER_ARRAY, next);
        } else if (!placed && idx >= 0) {
            int[] next = new int[list.length - 1];
            System.arraycopy(list, 0, next, 0, idx);
            System.arraycopy(list, idx + 1, next, idx, list.length - idx - 1);
            if (next.length == 0) {
                pdc.remove(placedKey);
            } else {
                pdc.set(placedKey, PersistentDataType.INTEGER_ARRAY, next);
            }
        }
        return idx >= 0;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Settings s = plugin.settings();
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (!event.isDropItems() || !s.oreBlocks.contains(block.getType())
                || !s.oreWorlds.contains(block.getWorld().getName()) || block.getY() >= s.oreMaxY) {
            return;
        }
        if (setPlaced(block, false)) {
            return; // bloc posé par un joueur : pas d'éclat
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (s.oreNoSilkTouch && tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0) {
            return;
        }
        int fortune = tool.getEnchantmentLevel(Enchantment.FORTUNE);
        double percent = s.orePercent * (1.0 + fortune * s.fortuneBonus);
        if (ThreadLocalRandom.current().nextDouble() * 100.0 >= percent) {
            return;
        }

        Location where = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().dropItemNaturally(where, plugin.items().shard(s.oreAmount));
        if (!s.oreMessage.isEmpty()) {
            Component msg = LegacyComponentSerializer.legacyAmpersand().deserialize(s.oreMessage);
            player.sendActionBar(msg);
        }
        if (!s.oreSound.isEmpty()) {
            try {
                player.playSound(Sound.sound(Key.key(s.oreSound), Sound.Source.PLAYER,
                        s.oreSoundVolume, s.oreSoundPitch));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("ore.sound invalide : " + s.oreSound);
            }
        }
    }
}
