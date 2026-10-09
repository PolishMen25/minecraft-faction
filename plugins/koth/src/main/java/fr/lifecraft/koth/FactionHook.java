package fr.lifecraft.koth;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

/**
 * Lien vers FactionsUUID par réflexion, pour ne pas dépendre de la version de son API
 * (les paquets ont changé entre les versions : com.massivecraft puis dev.kitteh).
 * Si rien n'est trouvé, chaque joueur compte comme sa propre équipe.
 */
final class FactionHook {

    /** Équipe d'un joueur : clé unique et nom affiché. */
    record Team(String key, String name, boolean faction) {
    }

    private static final String[] MANAGER_CLASSES = {
        "dev.kitteh.factions.FPlayers",
        "com.massivecraft.factions.FPlayers",
    };

    private Object manager;
    private Method byPlayer;

    FactionHook(Logger log) {
        for (String cls : MANAGER_CLASSES) {
            try {
                Class<?> c = Class.forName(cls);
                Object inst = null;
                for (String name : new String[] {"fPlayers", "getInstance", "instance"}) {
                    Method m = findMethod(c, name, 0);
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
        log.warning("FactionsUUID introuvable : chaque joueur comptera comme une équipe à part.");
    }

    Team teamOf(Player p) {
        String solo = "p:" + p.getUniqueId();
        if (manager == null) {
            return new Team(solo, p.getName(), false);
        }
        try {
            Object fp = byPlayer.invoke(manager, p);
            Object faction = call(fp, "faction", "getFaction");
            if (faction == null) {
                return new Team(solo, p.getName(), false);
            }
            Object normal = call(faction, "isNormal");
            Object wild = call(faction, "isWilderness");
            boolean isNormal = normal instanceof Boolean b ? b : !(wild instanceof Boolean w && w);
            if (!isNormal) {
                return new Team(solo, p.getName(), false);
            }
            Object id = call(faction, "id", "getId");
            Object tag = call(faction, "tag", "getTag");
            String name = tag != null ? tag.toString() : String.valueOf(id);
            return new Team("f:" + (id != null ? id : name), name, true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return new Team(solo, p.getName(), false);
        }
    }

    private static Object call(Object target, String... names) throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        for (String n : names) {
            Method m = findMethod(target.getClass(), n, 0);
            if (m != null) {
                m.setAccessible(true);
                return m.invoke(target);
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> c, String name, int params) {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == params) {
                return m;
            }
        }
        return null;
    }
}
