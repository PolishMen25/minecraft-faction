package fr.lifecraft.items;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

/** Valeurs de config.yml, lues une fois à chaque (re)chargement. */
final class Settings {

    final Set<String> oreWorlds;
    final int oreMaxY;
    final Set<Material> oreBlocks = new HashSet<>();
    final double orePercent;
    final double fortuneBonus;
    final boolean oreNoSilkTouch;
    final int oreAmount;
    final String oreMessage;
    final String oreSound;
    final float oreSoundVolume;
    final float oreSoundPitch;

    final double armorBonus;
    final double toughnessBonus;
    final double knockbackBonus;
    final double swordDamageBonus;
    final double pickaxeDamageBonus;

    final int shardsPerItem;

    final boolean anvilEnabled;
    final double repairFraction;
    final int levelsPerShard;

    Settings(LifeItemsPlugin plugin) {
        FileConfiguration c = plugin.getConfig();
        oreWorlds = new HashSet<>(c.getStringList("ore.worlds"));
        oreMaxY = c.getInt("ore.max-y", 16);
        List<String> names = c.getStringList("ore.blocks");
        for (String n : names) {
            Material m = Material.matchMaterial(n.toUpperCase(Locale.ROOT));
            if (m == null) {
                plugin.getLogger().warning("ore.blocks : matériau inconnu « " + n + " »");
            } else {
                oreBlocks.add(m);
            }
        }
        orePercent = Math.max(0, c.getDouble("ore.chance-percent", 0.4));
        fortuneBonus = Math.max(0, c.getDouble("ore.fortune-bonus-per-level", 0.5));
        oreNoSilkTouch = c.getBoolean("ore.disable-with-silk-touch", true);
        oreAmount = Math.max(1, c.getInt("ore.amount", 1));
        oreMessage = c.getString("ore.message", "");
        oreSound = c.getString("ore.sound", "");
        oreSoundVolume = (float) c.getDouble("ore.sound-volume", 1.0);
        oreSoundPitch = (float) c.getDouble("ore.sound-pitch", 0.8);

        armorBonus = c.getDouble("stats.armor-bonus", 1.0);
        toughnessBonus = c.getDouble("stats.toughness-bonus", 1.0);
        knockbackBonus = c.getDouble("stats.knockback-resistance-bonus", 0.0);
        swordDamageBonus = c.getDouble("stats.sword-damage-bonus", 1.0);
        pickaxeDamageBonus = c.getDouble("stats.pickaxe-damage-bonus", 0.0);

        shardsPerItem = Math.max(1, Math.min(8, c.getInt("recipes.shards-per-item", 8)));

        anvilEnabled = c.getBoolean("anvil.enabled", true);
        repairFraction = Math.max(0.01, Math.min(1.0, c.getDouble("anvil.repair-fraction-per-shard", 0.25)));
        levelsPerShard = Math.max(0, c.getInt("anvil.levels-per-shard", 1));
    }
}
