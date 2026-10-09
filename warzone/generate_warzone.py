#!/usr/bin/env python3
"""Génère le datapack de la warzone (faille, remontées, ponts, KOTH, avant-postes).

La warzone fait 75 × 75 chunks (1 200 × 1 200 blocs) autour du spawn. Une faille en anneau
carré de 40 blocs de large, profonde d'environ 52 blocs, l'entoure à 320 blocs du centre :
4 remontées dans les angles, 4 ponts au milieu des côtés. Entre le rempart du spawn et la faille,
l'arène KOTH ; au-delà de la faille, deux avant-postes.

Usage : python3 warzone/generate_warzone.py   (écrit warzone/datapack/lifecraft_warzone)
Puis sur le serveur : copier le dossier dans world/datapacks/, /reload,
/function lifecraft:warzone/build (puis attendre « Warzone : construction terminée »).

Le terrain naturel est très variable (montagne à l'ouest, mer à l'est et au sud) : les hauteurs
utilisées ici viennent d'un relevé du terrain fait sur le serveur le 9 octobre 2026.
"""
import json
import random
import shutil
from pathlib import Path

OUT = Path(__file__).resolve().parent / "datapack" / "lifecraft_warzone"
NS = "lifecraft"
FILL_LIMIT = 32768            # gamerule par défaut : blocs max par commande fill
STEP_BUDGET = 140_000         # blocs modifiés au plus par étape (évite de geler le serveur)
STEP_DELAY = "10t"            # délai entre deux étapes : laisse le temps de charger la zone suivante

# --------------------------------------------------------------------------------------------
# Géométrie (monde « world »). Centre de la warzone = centre du chunk du spawn (71.5, -2104.5).
# --------------------------------------------------------------------------------------------
WZ = (-528, -2704, 671, -1505)          # warzone : x1, z1, x2, z2
SPAWN = (-80, -2256, 223, -1953)        # spawn / safezone (304 × 304)
OUTER = (-288, -2464, 431, -1745)       # bord extérieur de la faille
INNER = (-248, -2424, 391, -1785)       # bord intérieur de la faille (exclu)
FLOOR_Y = 12                            # sol de la faille (on marche à Y=13)
SEAL_TOP = 64                           # murs étanches jusqu'à Y=64 (niveau de la mer : 63)
CARVE_TOP = {"nord": 110, "sud": 115, "ouest": 150, "est": 110}   # au-dessus du relief relevé

# Côtés de la faille : (nom, x1, z1, x2, z2)
SIDES = [
    ("nord", OUTER[0], OUTER[1], OUTER[2], INNER[1] - 1),
    ("sud", OUTER[0], INNER[3] + 1, OUTER[2], OUTER[3]),
    ("ouest", OUTER[0], INNER[1], INNER[0] - 1, INNER[3]),
    ("est", INNER[2] + 1, INNER[1], OUTER[2], INNER[3]),
]

# Remontées : angle, hauteur de marche en haut (terrain relevé à la sortie)
RAMPS = {"no": 73, "ne": 65, "so": 65, "se": 65}

# Ponts : nom, axe, centre (coordonnée transversale), niveau du tablier (bloc)
BRIDGES = [
    ("nord", "z", 72, 66),
    ("sud", "z", 72, 66),
    ("ouest", "x", -2088, 67),
    ("est", "x", -2104, 65),
]

# KOTH (entre le rempart du spawn et la faille, au nord) et avant-postes (au-delà de la faille)
KOTH = {"name": "Colline", "x": 72, "z": -2340, "floor": 78}
OUTPOSTS = [
    {"name": "Est", "x": 552, "z": -2104, "floor": 64},
    {"name": "Ouest", "x": -408, "z": -2104, "floor": 118},
]


# --------------------------------------------------------------------------------------------
# Outils
# --------------------------------------------------------------------------------------------
class Job:
    """Liste de commandes regroupées en étapes, chacune avec la zone à charger."""

    def __init__(self, name):
        self.name = name
        self.steps = []          # (zone (x1,z1,x2,z2), [commandes], volume)

    def add(self, area, cmds, volume):
        if self.steps and self.steps[-1][0] == area and self.steps[-1][2] + volume <= STEP_BUDGET:
            self.steps[-1][1].extend(cmds)
            self.steps[-1] = (area, self.steps[-1][1], self.steps[-1][2] + volume)
        else:
            self.steps.append((area, list(cmds), volume))


def split_box(x1, y1, z1, x2, y2, z2):
    """Découpe une boîte en sous-boîtes de FILL_LIMIT blocs au plus (sur l'axe le plus long)."""
    x1, x2 = sorted((x1, x2)); y1, y2 = sorted((y1, y2)); z1, z2 = sorted((z1, z2))
    vol = (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1)
    if vol <= FILL_LIMIT:
        return [(x1, y1, z1, x2, y2, z2)]
    dims = {"x": x2 - x1, "y": y2 - y1, "z": z2 - z1}
    axis = max(dims, key=dims.get)
    if axis == "x":
        m = (x1 + x2) // 2
        return split_box(x1, y1, z1, m, y2, z2) + split_box(m + 1, y1, z1, x2, y2, z2)
    if axis == "y":
        m = (y1 + y2) // 2
        return split_box(x1, y1, z1, x2, m, z2) + split_box(x1, m + 1, z1, x2, y2, z2)
    m = (z1 + z2) // 2
    return split_box(x1, y1, z1, x2, y2, m) + split_box(x1, y1, m + 1, x2, y2, z2)


def fill(job, box, block, mode="", area=None):
    """Ajoute des fill (découpés) ; chaque morceau est une unité d'étape avec sa propre zone."""
    for b in split_box(*box):
        vol = (b[3] - b[0] + 1) * (b[4] - b[1] + 1) * (b[5] - b[2] + 1)
        a = area or (b[0], b[2], b[3], b[5])
        job.add(a, [f"fill {b[0]} {b[1]} {b[2]} {b[3]} {b[4]} {b[5]} {block}{' ' + mode if mode else ''}"], vol)


def cmd(job, area, *cmds):
    job.add(area, cmds, len(cmds))


def chunk_area(area, margin=1):
    """Zone à forcer en chunks, avec une marge (les fill touchent parfois le bloc voisin)."""
    x1, z1, x2, z2 = area
    return (x1 - margin, z1 - margin, x2 + margin, z2 + margin)


def check_chunks(area):
    x1, z1, x2, z2 = area
    n = ((x2 >> 4) - (x1 >> 4) + 1) * ((z2 >> 4) - (z1 >> 4) + 1)
    assert n <= 256, f"zone trop grande pour forceload ({n} chunks) : {area}"


# --------------------------------------------------------------------------------------------
# Faille
# --------------------------------------------------------------------------------------------
def build_faille(job):
    # 1. Murs étanches sur les deux bords (2 blocs d'épaisseur), du fond jusqu'à SEAL_TOP :
    #    la mer et les nappes d'eau souterraines ne doivent pas pouvoir couler dans la faille.
    ox1, oz1, ox2, oz2 = OUTER
    ix1, iz1, ix2, iz2 = INNER
    y1, y2 = FLOOR_Y - 2, SEAL_TOP
    walls = [
        # extérieur
        (ox1 - 2, oz1 - 2, ox2 + 2, oz1 - 1), (ox1 - 2, oz2 + 1, ox2 + 2, oz2 + 2),
        (ox1 - 2, oz1, ox1 - 1, oz2), (ox2 + 1, oz1, ox2 + 2, oz2),
        # intérieur
        (ix1, iz1, ix2, iz1 + 1), (ix1, iz2 - 1, ix2, iz2),
        (ix1, iz1 + 2, ix1 + 1, iz2 - 2), (ix2 - 1, iz1 + 2, ix2, iz2 - 2),
    ]
    for (x1, z1, x2, z2) in walls:
        # tronçons de 32 blocs : une étape = une petite zone chargée
        for (a1, b1, a2, b2) in segments(x1, z1, x2, z2, 32):
            fill(job, (a1, y1, b1, a2, y2, b2), "minecraft:stone")
    job.add(None, ["say Warzone : murs de la faille posés, creusement..."], 0)

    # 2. Creusement, côté par côté, par tronçons de 16 blocs dans le sens de la longueur
    for name, x1, z1, x2, z2 in SIDES:
        top = CARVE_TOP[name]
        for (a1, b1, a2, b2) in segments(x1, z1, x2, z2, 16):
            area = (a1, b1, a2, b2)
            fill(job, (a1, FLOOR_Y - 2, b1, a2, FLOOR_Y - 1, b2), "minecraft:stone", area=area)
            fill(job, (a1, FLOOR_Y, b1, a2, FLOOR_Y, b2), "minecraft:tuff", area=area)
            fill(job, (a1, FLOOR_Y + 1, b1, a2, top, b2), "minecraft:air", area=area)
        job.add(None, [f"say Warzone : côté {name} de la faille creusé"], 0)

    # 3. Sol : taches de gravier, terre, mousse, blocs de pierre et quelques lumières
    rng = random.Random(1945)
    for name, x1, z1, x2, z2 in SIDES:
        for _ in range(max(x2 - x1, z2 - z1) // 6):
            w, d = rng.randint(2, 6), rng.randint(2, 6)
            px, pz = rng.randint(x1, max(x1, x2 - w)), rng.randint(z1, max(z1, z2 - d))
            block = rng.choice(["minecraft:gravel", "minecraft:coarse_dirt", "minecraft:moss_block",
                                "minecraft:cobbled_deepslate", "minecraft:andesite", "minecraft:mud"])
            area = (px, pz, px + w, pz + d)
            cmd(job, area, f"fill {px} {FLOOR_Y} {pz} {px + w} {FLOOR_Y} {pz + d} {block}")
            if rng.random() < 0.25:     # rocher
                cmd(job, area, f"fill {px} {FLOOR_Y + 1} {pz} {px + 1} {FLOOR_Y + 2} {pz + 1} minecraft:mossy_cobblestone")
            if rng.random() < 0.3:      # éclairage au sol (limite l'apparition de monstres)
                cmd(job, area, f"setblock {px + w // 2} {FLOOR_Y} {pz + d // 2} minecraft:shroomlight")


def segments(x1, z1, x2, z2, length):
    """Découpe un rectangle allongé en tronçons de `length` blocs dans sa longueur."""
    out = []
    if (x2 - x1) >= (z2 - z1):
        for a in range(x1, x2 + 1, length):
            out.append((a, z1, min(a + length - 1, x2), z2))
    else:
        for b in range(z1, z2 + 1, length):
            out.append((x1, b, x2, min(b + length - 1, z2)))
    return out


# --------------------------------------------------------------------------------------------
# Remontées : escalier de 5 de large le long des deux murs extérieurs de chaque angle
# --------------------------------------------------------------------------------------------
def build_ramp(job, corner, top):
    ox1, oz1, ox2, oz2 = OUTER
    sx = -1 if corner[1] == "o" else 1          # vers l'extérieur en x
    sz = -1 if corner[0] == "n" else 1          # vers l'extérieur en z
    cx = ox1 if sx < 0 else ox2                 # coin extérieur
    cz = oz1 if sz < 0 else oz2
    W = 5
    rise = top - (FLOOR_Y + 1)
    leg1 = min(34, rise)                         # 1er tronçon le long du mur nord/sud
    leg2 = rise - leg1                           # 2e tronçon le long du mur ouest/est
    area = (min(cx, cx - sx * 45), min(cz, cz - sz * 45), max(cx, cx - sx * 45), max(cz, cz - sz * 45))
    area = (area[0] - 14, area[1] - 14, area[2] + 14, area[3] + 14)
    check_chunks(chunk_area(area))
    cmds = []
    # tronçon 1 : contre le mur extérieur en z, monte en allant vers le coin (direction sx)
    zrow = sorted((cz, cz - sz * (W - 1)))
    face1 = "west" if sx < 0 else "east"
    for i in range(leg1):
        x = cx - sx * (W + leg1 - 1 - i)        # commence loin du coin, finit contre le palier
        y = FLOOR_Y + 1 + i
        cmds.append(f"fill {x} {FLOOR_Y + 1} {zrow[0]} {x} {y - 1} {zrow[1]} minecraft:stone_bricks")
        cmds.append(f"fill {x} {y} {zrow[0]} {x} {y} {zrow[1]} minecraft:stone_brick_stairs[facing={face1}]")
        rail_z = cz - sz * W                     # garde-corps côté vide
        cmds.append(f"setblock {x} {y + 1} {rail_z} minecraft:stone_brick_wall")
        if i % 8 == 4:
            cmds.append(f"setblock {x} {y + 2} {rail_z} minecraft:lantern")
    # palier dans le coin
    ly = FLOOR_Y + 1 + leg1
    xs = sorted((cx, cx - sx * (W - 1)))
    cmds.append(f"fill {xs[0]} {FLOOR_Y + 1} {zrow[0]} {xs[1]} {ly - 1} {zrow[1]} minecraft:stone_bricks")
    cmds.append(f"setblock {cx - sx * (W - 1)} {ly} {cz - sz * (W - 1)} minecraft:lantern")
    # tronçon 2 : contre le mur extérieur en x, monte en s'éloignant du coin (direction -sz)
    face2 = "south" if sz < 0 else "north"
    for i in range(leg2):
        z = cz - sz * (W + i)
        y = ly + i
        cmds.append(f"fill {xs[0]} {FLOOR_Y + 1} {z} {xs[1]} {y - 1} {z} minecraft:stone_bricks")
        cmds.append(f"fill {xs[0]} {y} {z} {xs[1]} {y} {z} minecraft:stone_brick_stairs[facing={face2}]")
        rail_x = cx - sx * W
        cmds.append(f"setblock {rail_x} {y + 1} {z} minecraft:stone_brick_wall")
        if i % 8 == 4:
            cmds.append(f"setblock {rail_x} {y + 2} {z} minecraft:lantern")
    # palier haut puis sortie à travers le mur extérieur, vers le terrain (tranchée ou chaussée)
    ztop = cz - sz * (W + leg2)
    zt = sorted((ztop, ztop - sz * (W - 1)))
    cmds.append(f"fill {xs[0]} {FLOOR_Y + 1} {zt[0]} {xs[1]} {top - 1} {zt[1]} minecraft:stone_bricks")
    ex = sorted((cx + sx * 1, cx + sx * 16))
    cmds.append(f"fill {ex[0]} {top - 12} {zt[0]} {ex[1]} {top - 1} {zt[1]} minecraft:stone_bricks replace #minecraft:replaceable")
    cmds.append(f"fill {ex[0]} {top - 1} {zt[0]} {ex[1]} {top - 1} {zt[1]} minecraft:stone_bricks")
    cmds.append(f"fill {ex[0]} {top} {zt[0]} {ex[1]} {top + 5} {zt[1]} minecraft:air")
    cmds.append(f"fill {xs[0]} {top} {zt[0]} {xs[1]} {top + 5} {zt[1]} minecraft:air")
    cmd(job, area, *cmds)


# --------------------------------------------------------------------------------------------
# Ponts : tablier de 7 de large, parapets, piles dans la faille, chaussée de part et d'autre
# --------------------------------------------------------------------------------------------
def build_bridge(job, name, axis, center, deck):
    W = 3                                         # demi-largeur (7 blocs)
    APPROACH = 14
    if axis == "z":                               # pont nord/sud : franchit la faille en z
        side = next(s for s in SIDES if s[0] == name)
        z1, z2 = side[2] - APPROACH - 2, side[4] + APPROACH + 2
        a1, a2 = center - W, center + W
        area = (a1 - 2, z1, a2 + 2, z2)
        box = lambda u1, v1, u2, v2, y1, y2: (u1, y1, v1, u2, y2, v2)  # u=x, v=z
        along = (z1, z2); band = (side[2], side[4])
        rail_pos = (a1 - 1, a2 + 1)
        mk = lambda u, v: (u, v)
    else:                                         # pont ouest/est : franchit la faille en x
        side = next(s for s in SIDES if s[0] == name)
        z1, z2 = side[1] - APPROACH - 2, side[3] + APPROACH + 2
        a1, a2 = center - W, center + W
        area = (z1, a1 - 2, z2, a2 + 2)
        box = lambda u1, v1, u2, v2, y1, y2: (v1, y1, u1, v2, y2, u2)  # u=z, v=x
        along = (z1, z2); band = (side[1], side[3])
        rail_pos = (a1 - 1, a2 + 1)
        mk = lambda u, v: (v, u)
    check_chunks(chunk_area(area))
    lo, hi = along
    # chaussée : remblai sous le tablier (remplace l'air et l'eau), dégagement au-dessus
    fill(job, box(a1 - 1, lo, a2 + 1, hi, deck - 14, deck - 1), "minecraft:stone_bricks", "replace #minecraft:replaceable", area=area)
    fill(job, box(a1 - 1, lo, a2 + 1, hi, deck - 14, deck - 1), "minecraft:stone_bricks", "replace minecraft:water", area=area)
    fill(job, box(a1 - 1, lo, a2 + 1, hi, deck + 1, deck + 8), "minecraft:air", area=area)
    # au-dessus de la faille, on retire ce remblai pour ne garder que le tablier et les piles
    fill(job, box(a1 - 1, band[0], a2 + 1, band[1], FLOOR_Y + 1, deck - 2), "minecraft:air", area=area)
    fill(job, box(a1 - 1, band[0], a2 + 1, band[1], deck - 1, deck - 1), "minecraft:stone_brick_slab[type=top]", area=area)
    # tablier et parapets
    fill(job, box(a1, lo, a2, hi, deck, deck), "minecraft:polished_andesite", area=area)
    fill(job, box(center, lo, center, hi, deck, deck), "minecraft:stone_bricks", area=area)
    cmds = []
    for r in rail_pos:
        x, z = mk(r, lo); x2, z2 = mk(r, hi)
        cmds.append(f"fill {min(x, x2)} {deck + 1} {min(z, z2)} {max(x, x2)} {deck + 1} {max(z, z2)} minecraft:stone_brick_wall")
        for u in range(lo, hi + 1, 8):
            px, pz = mk(r, u)
            cmds.append(f"setblock {px} {deck + 2} {pz} minecraft:stone_brick_wall")
            cmds.append(f"setblock {px} {deck + 3} {pz} minecraft:lantern")
    # deux piles dans la faille
    span = band[1] - band[0]
    for t in (band[0] + span // 3, band[0] + 2 * span // 3):
        b = box(a1, t - 1, a2, t + 1, FLOOR_Y + 1, deck - 2)
        cmds.append(f"fill {b[0]} {b[1]} {b[2]} {b[3]} {b[4]} {b[5]} minecraft:stone_bricks")
    cmd(job, area, *cmds)


# --------------------------------------------------------------------------------------------
# Plateformes : KOTH (or, balise jaune) et avant-postes (palissade, tours, balise teintée par le plugin)
# --------------------------------------------------------------------------------------------
def platform(job, x, z, floor, r, ground_block):
    area = (x - r - 2, z - r - 2, x + r + 2, z + r + 2)
    check_chunks(chunk_area(area))
    fill(job, (x - r - 1, floor + 1, z - r - 1, x + r + 1, floor + 30, z + r + 1), "minecraft:air", area=area)
    fill(job, (x - r, floor - 12, z - r, x + r, floor - 1, z + r), "minecraft:stone_bricks", area=area)
    fill(job, (x - r, floor, z - r, x + r, floor, z + r), ground_block, area=area)
    fill(job, (x - r - 1, floor, z - r - 1, x + r + 1, floor, z + r + 1), "minecraft:stone_brick_slab", "replace minecraft:air", area=area)
    return area


def build_koth(job):
    x, z, f = KOTH["x"], KOTH["z"], KOTH["floor"]
    area = platform(job, x, z, f, 8, "minecraft:stone_bricks")
    cmd(job, area,
        f"fill {x - 6} {f} {z - 6} {x + 6} {f} {z + 6} minecraft:polished_andesite",
        f"fill {x - 4} {f} {z - 4} {x + 4} {f} {z + 4} minecraft:gold_block",
        f"fill {x - 3} {f} {z - 3} {x + 3} {f} {z + 3} minecraft:polished_blackstone_bricks",
        f"fill {x - 1} {f - 1} {z - 1} {x + 1} {f - 1} {z + 1} minecraft:iron_block",
        f"setblock {x} {f} {z} minecraft:beacon",
        f"setblock {x} {f + 1} {z} minecraft:yellow_stained_glass",
        *[c for dx, dz in ((-8, -8), (8, -8), (-8, 8), (8, 8)) for c in (
            f"fill {x + dx} {f + 1} {z + dz} {x + dx} {f + 3} {z + dz} minecraft:stone_brick_wall",
            f"setblock {x + dx} {f + 4} {z + dz} minecraft:lantern")])


def build_outpost(job, o):
    x, z, f = o["x"], o["z"], o["floor"]
    R = 10
    area = platform(job, x, z, f, R, "minecraft:spruce_planks")
    cmds = [
        # zone de capture 7×7 en pierre, balise sous le verre du centre (couleur posée par le plugin)
        f"fill {x - 3} {f} {z - 3} {x + 3} {f} {z + 3} minecraft:polished_deepslate",
        f"fill {x - 4} {f} {z - 4} {x + 4} {f} {z - 4} minecraft:chiseled_stone_bricks",
        f"fill {x - 4} {f} {z + 4} {x + 4} {f} {z + 4} minecraft:chiseled_stone_bricks",
        f"fill {x - 4} {f} {z - 3} {x - 4} {f} {z + 3} minecraft:chiseled_stone_bricks",
        f"fill {x + 4} {f} {z - 3} {x + 4} {f} {z + 3} minecraft:chiseled_stone_bricks",
        f"fill {x - 1} {f - 2} {z - 1} {x + 1} {f - 2} {z + 1} minecraft:iron_block",
        f"setblock {x} {f - 1} {z} minecraft:beacon",
        f"setblock {x} {f} {z} minecraft:glass",
    ]
    # palissade en rondins sur le pourtour, avec 4 ouvertures au milieu des côtés
    for (a, b, c, d) in ((x - R, z - R, x + R, z - R), (x - R, z + R, x + R, z + R),
                         (x - R, z - R, x - R, z + R), (x + R, z - R, x + R, z + R)):
        cmds.append(f"fill {a} {f + 1} {b} {c} {f + 2} {d} minecraft:spruce_log")
        cmds.append(f"fill {a} {f + 3} {b} {c} {f + 3} {d} minecraft:spruce_fence")
    for dx, dz in ((0, -R), (0, R), (-R, 0), (R, 0)):
        if dx == 0:
            cmds.append(f"fill {x - 1} {f + 1} {z + dz} {x + 1} {f + 3} {z + dz} minecraft:air")
        else:
            cmds.append(f"fill {x + dx} {f + 1} {z - 1} {x + dx} {f + 3} {z + 1} minecraft:air")
    # 4 tours de guet aux angles
    for dx, dz in ((-R, -R), (R, -R), (-R, R), (R, R)):
        tx, tz = x + dx, z + dz
        cmds += [
            f"fill {tx - 1} {f + 1} {tz - 1} {tx + 1} {f + 6} {tz + 1} minecraft:stripped_spruce_log",
            # échelle sur la face intérieure de la tour (côté avant-poste), trappe au-dessus
            f"fill {tx} {f + 1} {tz - 2 * (1 if dz > 0 else -1)} {tx} {f + 7} {tz - 2 * (1 if dz > 0 else -1)} minecraft:ladder[facing={'south' if dz < 0 else 'north'}]",
            f"fill {tx - 2} {f + 7} {tz - 2} {tx + 2} {f + 7} {tz + 2} minecraft:spruce_planks replace #minecraft:replaceable",
            f"setblock {tx} {f + 7} {tz - 2 * (1 if dz > 0 else -1)} minecraft:spruce_trapdoor[half=bottom,facing={'south' if dz < 0 else 'north'}]",
            f"fill {tx - 2} {f + 8} {tz - 2} {tx + 2} {f + 8} {tz + 2} minecraft:spruce_fence",
            f"fill {tx - 1} {f + 8} {tz - 1} {tx + 1} {f + 8} {tz + 1} minecraft:air",
            f"setblock {tx} {f + 9} {tz} minecraft:lantern",
        ]
    # bannières et lanternes à l'intérieur
    for dx, dz in ((-6, -6), (6, -6), (-6, 6), (6, 6)):
        cmds += [f"fill {x + dx} {f + 1} {z + dz} {x + dx} {f + 2} {z + dz} minecraft:spruce_fence",
                 f"setblock {x + dx} {f + 3} {z + dz} minecraft:lantern"]
    cmd(job, area, *cmds)


# --------------------------------------------------------------------------------------------
# Écriture du datapack
# --------------------------------------------------------------------------------------------
def write(job):
    if OUT.exists():
        shutil.rmtree(OUT)
    fdir = OUT / "data" / NS / "function" / "warzone"
    fdir.mkdir(parents=True)
    (OUT / "pack.mcmeta").write_text(json.dumps(
        {"pack": {"description": "Warzone LifeCraft : faille, ponts, remontées, KOTH, avant-postes",
                  "min_format": 107, "max_format": 107}}, ensure_ascii=False, indent=2) + "\n")

    # Fusionne les étapes sans zone (messages) avec l'étape précédente
    steps = []
    for area, cmds, vol in job.steps:
        if area is None and steps:
            steps[-1][1].extend(cmds)
        else:
            steps.append((area, list(cmds), vol))
    for area, _, _ in steps:
        if area:
            check_chunks(chunk_area(area))

    n = len(steps)
    total = sum(len(c) for _, c, _ in steps)
    fl = lambda a: "{} {} {} {}".format(*chunk_area(a)) if a else None

    first = steps[0][0]
    (fdir / "build.mcfunction").write_text(
        "# Construit la warzone par étapes (environ {} s). Ne pas lancer deux fois en même temps.\n"
        "say Warzone : début de la construction ({} étapes)\n"
        "forceload add {}\n"
        "schedule function {}:warzone/step_0 40t\n".format(n // 2 + 2, n, fl(first), NS))
    for i, (area, cmds, _) in enumerate(steps):
        lines = [f"# Étape {i + 1}/{n}"]
        if i + 1 < n and steps[i + 1][0]:
            lines.append(f"forceload add {fl(steps[i + 1][0])}")
        lines += cmds
        if area:
            lines.append(f"forceload remove {fl(area)}")
        if (i + 1) % max(1, n // 10) == 0:
            lines.append(f"say Warzone : {100 * (i + 1) // n} %")
        if i + 1 < n:
            lines.append(f"schedule function {NS}:warzone/step_{i + 1} {STEP_DELAY}")
        else:
            lines.append("say Warzone : construction terminée")
        (fdir / f"step_{i}.mcfunction").write_text("\n".join(lines) + "\n")
    return n, total


def main():
    job = Job("warzone")
    build_faille(job)
    job.add(None, ["say Warzone : faille creusée, construction des remontées et des ponts..."], 0)
    for corner, top in RAMPS.items():
        build_ramp(job, corner, top)
    for b in BRIDGES:
        build_bridge(job, *b)
    build_koth(job)
    for o in OUTPOSTS:
        build_outpost(job, o)
    n, total = write(job)
    print(f"{OUT} : {n} étapes, {total} commandes")


if __name__ == "__main__":
    main()
