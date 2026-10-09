package fr.lifecraft.core;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

/**
 * Lien vers FactionsUUID par réflexion, sans dépendance de compilation.
 * Méthodes utilisées (FactionsUUID 4.7.2, paquet dev.kitteh.factions) :
 * FPlayer#faction, #power, #powerMax ; Faction#tag, #isNormal, #power, #powerMax, #claimCount.
 * Les anciens noms (com.massivecraft : getFaction, getPower...) sont essayés en repli.
 * Toute valeur introuvable devient « ? ».
 */
final class FactionHook {

    /** Données de faction d'un joueur, déjà en texte. */
    record Info(boolean hasFaction, String tag, String power, String maxPower,
                String factionPower, String factionMaxPower, String lands) {
    }

    private static final Info UNKNOWN = new Info(false, null, "?", "?", "?", "?", "?");

    private static final String[] MANAGER_CLASSES = {
        "dev.kitteh.factions.FPlayers",
        "com.massivecraft.factions.FPlayers",
    };

    private final Map<String, Method> cache = new ConcurrentHashMap<>();
    private Object manager;
    private Method byPlayer;

    FactionHook(Logger log) {
        for (String cls : MANAGER_CLASSES) {
            try {
                Class<?> c = Class.forName(cls);
                Object inst = null;
                for (String name : new String[] {"fPlayers", "getInstance", "instance"}) {
                    Method m = find(c, name, 0);
                    if (m != null && Modifier.isStatic(m.getModifiers())) {
                        inst = m.invoke(null);
                        break;
                    }
                }
                if (inst == null) {
                    continue;
                }
                for (Method m : inst.getClass().getMethods()) {
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(Player.class)
                            && m.getReturnType() != void.class
                            && (m.getName().equals("get") || m.getName().equals("getByPlayer"))) {
                        m.setAccessible(true);
                        byPlayer = m;
                        break;
                    }
                }
                if (byPlayer != null) {
                    manager = inst;
                    log.info("Intégration Factions active (" + cls + "#" + byPlayer.getName() + ")");
                    return;
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // classe absente ou API différente : on essaie la suivante
            }
        }
        log.warning("FactionsUUID introuvable : les valeurs de faction afficheront « ? ».");
    }

    boolean available() {
        return manager != null;
    }

    /** Tag de la faction du joueur, ou null s'il n'en a pas (ou si Factions est absent). */
    String tagOf(Player p) {
        Info i = info(p);
        return i.hasFaction() ? i.tag() : null;
    }

    Info info(Player p) {
        if (manager == null) {
            return UNKNOWN;
        }
        try {
            Object fp = byPlayer.invoke(manager, p);
            if (fp == null) {
                return UNKNOWN;
            }
            String power = num(call(fp, "powerRounded", "getPowerRounded", "power", "getPower"));
            String max = num(call(fp, "powerMaxRounded", "getPowerMaxRounded", "powerMax", "getPowerMax"));
            Object faction = call(fp, "faction", "getFaction");
            if (faction == null || !isNormal(faction)) {
                return new Info(false, null, power, max, "-", "-", "-");
            }
            Object tag = call(faction, "tag", "getTag");
            return new Info(true, tag != null ? tag.toString() : "?", power, max,
                num(call(faction, "power", "getPowerRounded")),
                num(call(faction, "powerMax", "getPowerMaxRounded")),
                num(call(faction, "claimCount", "getLandRounded")));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return UNKNOWN;
        }
    }

    private boolean isNormal(Object faction) throws ReflectiveOperationException {
        Object normal = call(faction, "isNormal");
        if (normal instanceof Boolean b) {
            return b;
        }
        Object wild = call(faction, "isWilderness");
        return !(wild instanceof Boolean w && w);
    }

    private static String num(Object o) {
        if (o instanceof Double d) {
            return String.valueOf(Math.round(d));
        }
        return o == null ? "?" : o.toString();
    }

    private Object call(Object target, String... names) throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        for (String n : names) {
            String key = target.getClass().getName() + "#" + n;
            Method m = cache.get(key);
            if (m == null) {
                m = find(target.getClass(), n, 0);
                if (m == null) {
                    continue;
                }
                m.setAccessible(true);
                cache.put(key, m);
            }
            return m.invoke(target);
        }
        return null;
    }

    private static Method find(Class<?> c, String name, int params) {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == params) {
                return m;
            }
        }
        return null;
    }
}
