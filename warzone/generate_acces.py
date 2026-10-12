#!/usr/bin/env python3
"""Génère les accès entre l'île du spawn (factions.zip) et les ponts de la faille, et la jetée du KOTH.

L'île est en contrebas du terrain d'origine (sol à Y≈50-60 contre 62-75 autour) : sans aide, on ne
monte pas du spawn sur les ponts nord, sud et ouest. Pour chaque pont, on suit l'axe du pont vers
l'île en partant du tablier ; la chaussée colle au sol (médiane de sa largeur) mais ne change que d'un bloc à la fois
(escaliers). Seule la partie où elle s'écarte du sol est construite : remblai dessous, dégagement
au-dessus, bordures, garde-corps quand le vide dépasse un bloc, lanternes.

Le KOTH (72, 64, -1625) est en mer, à ~100 blocs du pont sud : une jetée de 7 de large y mène depuis
le bout du pont, à Y 64, en entaillant la butte qu'elle traverse.

Le profil se calcule sur le monde réel (cartes de hauteur des fichiers région, sans feuillage) :
lancer `save-all flush` avant, puis
  python3 -I warzone/generate_acces.py   (écrit warzone/datapack/lifecraft_acces)
Sur le serveur : copier le dossier dans world/datapacks/, /reload, /function lifecraft:acces/build.
"""
import json
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.dont_write_bytecode = True
sys.path.insert(0, str(HERE))
import generate_warzone as wz  # noqa: E402
from anvil import HeightMap  # noqa: E402

OUT = HERE / "datapack" / "lifecraft_acces"
NS = wz.NS
W = 3                       # demi-largeur de la chaussée : 7 blocs, comme les ponts
FILL_DEPTH = 24             # remblai sous la chaussée (falaise nord : ~17 blocs)
CLEAR = 6                   # dégagement au-dessus
APPROACH = 14 + 2           # longueur des chaussées des ponts au-delà de la faille (build_bridge)
MAX_LEN = {"nord": 50, "sud": 25, "ouest": 12, "est": 12}


def bridge_end(name):
    """(x, z) du dernier bloc de tablier côté île, direction (dx, dz) vers l'île, niveau du tablier."""
    _, axis, center, deck = next(b for b in wz.BRIDGES if b[0] == name)
    if name == "nord":
        return (center, wz.INNER[1] + APPROACH - 1), (0, 1), deck
    if name == "sud":
        return (center, wz.INNER[3] - APPROACH + 1), (0, -1), deck
    if name == "ouest":
        return (wz.INNER[0] + APPROACH - 1, center), (1, 0), deck
    return (wz.INNER[2] - APPROACH + 1, center), (-1, 0), deck


FACING = {(0, 1): "south", (0, -1): "north", (1, 0): "east", (-1, 0): "west"}


def plan(name, hmap):
    """Profil (positions, sol, chaussée) et segment à construire [i1, i2] (None si rien)."""
    (x0, z0), (dx, dz), deck = bridge_end(name)
    across = lambda x, z, k: (x + k * dz * dz, z + k * dx * dx)       # décalage transversal
    pos, ground, path = [(x0, z0)], [deck], [deck]
    for i in range(1, MAX_LEN[name] + 1):
        x, z = x0 + dx * i, z0 + dz * i
        g = sorted(hmap(*across(x, z, k)) for k in range(-W, W + 1))[W]      # médiane : ignore un tronc
        pos.append((x, z))
        ground.append(g)
        path.append(max(min(g, path[-1] + 1), path[-1] - 1))
    # écarts d'au moins 2 blocs : là où l'on ne passe pas à pied (1 bloc se franchit en sautant)
    diff = [i for i in range(1, len(path)) if abs(path[i] - ground[i]) >= 2]
    if not diff:
        return pos, ground, path, None, (dx, dz)
    i1, i2 = max(1, diff[0] - 2), min(len(path) - 1, diff[-1] + 3)
    assert abs(path[i2] - ground[i2]) <= 1, f"{name} : la chaussée ne rejoint pas le sol, allonger MAX_LEN"
    return pos, ground, path, (i1, i2), (dx, dz)


def plan_koth(hmap):
    """Jetée du bout extérieur du pont sud jusqu'à la plateforme du KOTH, au-dessus de la mer.

    Elle descend du tablier (Y 66) au sol de la plateforme (Y 64), puis reste à plat : la butte
    traversée est entaillée (dégagement adapté).
    """
    _, _, center, deck = next(b for b in wz.BRIDGES if b[0] == "sud")
    x0, z0 = center, wz.OUTER[3] + APPROACH                     # dernier bloc de tablier côté sud
    z_end = wz.KOTH["z"] - 8 - 2                                 # juste avant la bordure de la plateforme
    pos, ground, path = [(x0, z0)], [deck], [deck]
    for z in range(z0 + 1, z_end + 1):
        pos.append((x0, z))
        ground.append(sorted(hmap(x0 + k, z) for k in range(-W, W + 1))[W])
        path.append(max(wz.KOTH["floor"], path[-1] - 1))
    diff = [i for i in range(1, len(path)) if abs(path[i] - ground[i]) >= 2]
    return pos, ground, path, ((1, len(path) - 1) if diff else None), (0, 1)


def commands(name, plan_, hmap):
    pos, ground, path, seg, (dx, dz) = plan_
    if seg is None:
        return [], None
    clear = max([CLEAR] + [g - y + 2 for g, y in zip(ground, path)])   # entaille les buttes
    across = lambda x, z, k: (x + k * dz * dz, z + k * dx * dx)
    cmds = []
    i1, i2 = seg
    for i in range(i1, i2 + 1):
        y = path[i]
        (ax, az), (bx, bz) = across(*pos[i], -W - 1), across(*pos[i], W + 1)
        (cx1, cz1), (cx2, cz2) = across(*pos[i], -W - 2), across(*pos[i], W + 2)
        x1, x2, z1, z2 = min(ax, bx), max(ax, bx), min(az, bz), max(az, bz)
        cmds.append(f"fill {x1} {y - FILL_DEPTH} {z1} {x2} {y - 1} {z2} minecraft:stone_bricks replace #minecraft:replaceable")
        cmds.append(f"fill {x1} {y - FILL_DEPTH} {z1} {x2} {y - 1} {z2} minecraft:stone_bricks replace minecraft:water")
        cmds.append(f"fill {min(cx1, cx2)} {y + 1} {min(cz1, cz2)} {max(cx1, cx2)} {y + clear} {max(cz1, cz2)} minecraft:air")
        (px1, pz1), (px2, pz2) = across(*pos[i], -W), across(*pos[i], W)
        cmds.append(f"fill {min(px1, px2)} {y} {min(pz1, pz2)} {max(px1, px2)} {y} {max(pz1, pz2)} minecraft:polished_andesite")
        cmds.append(f"setblock {pos[i][0]} {y} {pos[i][1]} minecraft:stone_bricks")
        for k in (-W - 1, W + 1):
            bxk, bzk = across(*pos[i], k)
            cmds.append(f"setblock {bxk} {y} {bzk} minecraft:chiseled_stone_bricks")
            if hmap(bxk, bzk) < y - 1:                      # vide sous la bordure : garde-corps
                cmds.append(f"setblock {bxk} {y + 1} {bzk} minecraft:stone_brick_wall")
            if (i - i1) % 8 == 4:
                cmds.append(f"fill {bxk} {y + 1} {bzk} {bxk} {y + 3} {bzk} minecraft:stone_brick_wall")
                cmds.append(f"setblock {bxk} {y + 4} {bzk} minecraft:lantern")
    # escaliers sur la chaussée à chaque changement de niveau
    d = (dx, dz)
    for i in range(i1, i2 + 1):
        if path[i] == path[i - 1] - 1:                          # descente vers l'île : marche en i-1
            j, y, f = i - 1, path[i - 1], FACING[(-d[0], -d[1])]
        elif path[i] == path[i - 1] + 1:                        # montée vers l'île : marche en i
            j, y, f = i, path[i], FACING[d]
        else:
            continue
        (px1, pz1), (px2, pz2) = across(*pos[j], -W), across(*pos[j], W)
        cmds.append(f"fill {min(px1, px2)} {y} {min(pz1, pz2)} {max(px1, px2)} {y} {max(pz1, pz2)} "
                    f"minecraft:stone_brick_stairs[facing={f}]")
    xs = [p[0] for p in pos[i1:i2 + 1]]
    zs = [p[1] for p in pos[i1:i2 + 1]]
    area = (min(xs) - W - 3, min(zs) - W - 3, max(xs) + W + 3, max(zs) + W + 3)
    return cmds, area


def main():
    names = [b[0] for b in wz.BRIDGES]
    hmap = HeightMap(wz.OUTER[0], wz.OUTER[1], wz.OUTER[2], wz.KOTH["z"])
    if OUT.exists():
        shutil.rmtree(OUT)
    fdir = OUT / "data" / NS / "function" / "acces"
    fdir.mkdir(parents=True)
    (OUT / "pack.mcmeta").write_text(json.dumps(
        {"pack": {"description": "Accès LifeCraft : île du spawn ↔ ponts de la faille",
                  "min_format": 107, "max_format": 107}}, ensure_ascii=False, indent=2) + "\n")
    build = ["# Construit les accès île ↔ ponts et la jetée du KOTH (quelques secondes)."]
    delay = 40
    plans = {name: plan(name, hmap) for name in names}
    plans["koth"] = plan_koth(hmap)
    for name, p in plans.items():
        cmds, area = commands(name, p, hmap)
        pos, ground, path, seg, _ = p
        if area is None:
            print(f"{name} : le terrain rejoint déjà la chaussée, rien à construire")
            continue
        a = "{} {} {} {}".format(*area)
        build += [f"forceload add {a}", f"schedule function {NS}:acces/{name} {delay}t append"]
        (fdir / f"{name}.mcfunction").write_text("\n".join(
            [f"# Accès {name} : chaussée de {seg[1] - seg[0] + 1} blocs"] + cmds +
            [f"forceload remove {a}", f"say Accès {name} : construit"]) + "\n")
        delay += 20
        print(f"{name} : {seg[1] - seg[0] + 1} blocs, chaussée {path[seg[0]:seg[1] + 1]}, {len(cmds)} commandes")
    (fdir / "build.mcfunction").write_text("\n".join(build) + "\n")


if __name__ == "__main__":
    main()
