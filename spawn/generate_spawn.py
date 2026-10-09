#!/usr/bin/env python3
"""
Génère le spawn médiéval 64x64 du serveur Faction sous forme de structure
vanilla (.nbt), à poser en jeu avec /place template.

Usage : python3 generate_spawn.py            -> spawn_medieval.nbt + preview.png
        python3 generate_spawn.py --no-preview

Aucune dépendance pour le .nbt ; l'aperçu nécessite Pillow.
"""
import argparse
import gzip
import math
import random
import struct
from pathlib import Path

SIZE_X, SIZE_Y, SIZE_Z = 64, 41, 64
# Fondation épaisse et grand volume dégagé : le spawn se pose sur un terrain en pente,
# il comble les creux jusqu'à GROUND blocs sous le sol et rase le relief au-dessus.
GROUND = 10                     # couche du sol (y local) ; 0..GROUND-1 = fondations
C = 31.5                        # centre de la place
# DataVersion 1.21 : le jeu met la structure à niveau automatiquement (DataFixer)
DATA_VERSION = 3953

DIRS = ["north", "east", "south", "west"]
VEC = {"north": (0, -1), "east": (1, 0), "south": (0, 1), "west": (-1, 0)}

rng = random.Random(1337)
grid = {}                       # (x, y, z) -> (name, props)


# --------------------------------------------------------------------------
# Placement avec rotation autour du centre (k quarts de tour horaires)
# --------------------------------------------------------------------------
def rot_xz(x, z, k):
    for _ in range(k % 4):
        x, z = SIZE_X - 1 - z, x
    return x, z


def rot_facing(f, k):
    return DIRS[(DIRS.index(f) + k) % 4] if f in DIRS else f


def put(x, y, z, name, props=None, k=0):
    x, z = rot_xz(x, z, k)
    if not (0 <= x < SIZE_X and 0 <= y < SIZE_Y and 0 <= z < SIZE_Z):
        return
    props = dict(props or {})
    if "facing" in props:
        props["facing"] = rot_facing(props["facing"], k)
    if "axis" in props and props["axis"] in ("x", "z") and k % 2:
        props["axis"] = "z" if props["axis"] == "x" else "x"
    grid[(x, y, z)] = (name, props)


def get(x, y, z):
    return grid.get((x, y, z), ("minecraft:air", {}))[0]


def dist(x, z):
    return math.hypot(x - C, z - C)


def mc(n):
    return n if ":" in n else "minecraft:" + n


def brick():
    r = rng.random()
    return "stone_bricks" if r < .62 else "mossy_stone_bricks" if r < .82 else "cracked_stone_bricks"


def cobble():
    r = rng.random()
    return "cobblestone" if r < .5 else "mossy_cobblestone" if r < .7 else "andesite" if r < .9 else "gravel"


STAIRS = lambda f, half="bottom": {"facing": f, "half": half, "shape": "straight", "waterlogged": "false"}
SLAB = lambda t="bottom": {"type": t, "waterlogged": "false"}
LANTERN = lambda hanging: {"hanging": "true" if hanging else "false", "waterlogged": "false"}
LEAVES = {"distance": "1", "persistent": "true", "waterlogged": "false"}


def P(x, y, z, n, props=None, k=0):
    put(x, y, z, mc(n), props, k)


# --------------------------------------------------------------------------
# Terrain : fondations, herbe, place pavée, allées
# --------------------------------------------------------------------------
def in_path(x, z):
    """Allées N/S/E/O de 6 de large (bordures comprises)."""
    return (28 <= x <= 35) or (28 <= z <= 35)


for x in range(SIZE_X):
    for z in range(SIZE_Z):
        for y in range(GROUND):
            P(x, y, z, "dirt" if y >= GROUND - 3 else "stone")
        P(x, GROUND, z, "grass_block", {"snowy": "false"})

for x in range(SIZE_X):
    for z in range(SIZE_Z):
        r = dist(x, z)
        if r <= 15:
            P(x, GROUND, z, "polished_andesite" if 9 <= r <= 10 else brick())
        elif r <= 16.2:
            P(x, GROUND, z, "polished_andesite")
        elif 29 <= x <= 34 or 29 <= z <= 34:
            # allée : cœur pavé, bordure en pierre taillée
            edge = x in (29, 34) if (29 <= x <= 34 and not 29 <= z <= 34) else z in (29, 34)
            P(x, GROUND, z, "stone_bricks" if edge else cobble())

# --------------------------------------------------------------------------
# Fontaine centrale à deux niveaux
# --------------------------------------------------------------------------
for x in range(SIZE_X):
    for z in range(SIZE_Z):
        r = dist(x, z)
        if r <= 4.2:
            P(x, GROUND + 1, z, "water", {"level": "0"})
        elif r <= 5.3:
            P(x, GROUND + 1, z, "stone_bricks")
            P(x, GROUND + 2, z, "stone_brick_slab", SLAB())
for y in range(GROUND + 1, GROUND + 5):
    for x in (31, 32):
        for z in (31, 32):
            P(x, y, z, "chiseled_stone_bricks")
top = GROUND + 5
for x in range(30, 34):
    for z in range(30, 34):
        P(x, top - 1, z, "chiseled_stone_bricks" if x in (31, 32) and z in (31, 32) else "stone_brick_slab", None if x in (31, 32) and z in (31, 32) else SLAB("top"))
        if x in (31, 32) and z in (31, 32):
            P(x, top, z, "water", {"level": "0"})
        else:
            P(x, top, z, "stone_bricks")
for x, z in ((30, 30), (33, 30), (30, 33), (33, 33)):
    P(x, top + 1, z, "lantern", LANTERN(False))
# lanternes sur le rebord du bassin
for ang in range(45, 360, 90):
    a = math.radians(ang)
    P(round(C + 4.9 * math.cos(a)), GROUND + 2, round(C + 4.9 * math.sin(a)), "lantern", LANTERN(False))

# --------------------------------------------------------------------------
# Lampadaires (place + allées)
# --------------------------------------------------------------------------
def lamp_post(x, z, k=0, h=3):
    for y in range(GROUND + 1, GROUND + 1 + h):
        P(x, y, z, "dark_oak_fence", {}, k)
    P(x, GROUND + 1 + h, z, "lantern", LANTERN(False), k)


for ang in range(45, 360, 90):
    a = math.radians(ang)
    lamp_post(round(C + 13 * math.cos(a)), round(C + 13 * math.sin(a)))

for k in range(4):
    for z in (6, 12):              # côté nord, tourné pour les 4 allées
        lamp_post(28, z, k); lamp_post(35, z, k)

# bancs autour de la place, face à la fontaine
for k in range(4):
    for x in (24, 25, 38, 39):
        P(x, GROUND + 1, 20, "spruce_stairs", STAIRS("north"), k)

# --------------------------------------------------------------------------
# Arches d'entrée médiévales (une par allée)
# --------------------------------------------------------------------------
def arch(k):
    for tx in (25, 36):            # deux tours 3x3
        for x in range(tx, tx + 3):
            for z in range(0, 3):
                P(x, GROUND, z, "stone_bricks", None, k)
                for y in range(GROUND + 1, GROUND + 10):
                    P(x, y, z, brick(), None, k)
                if (x + z) % 2 == 0:
                    P(x, GROUND + 10, z, "stone_bricks", None, k)   # créneaux
        P(tx + 1, GROUND + 7, 3, "red_wall_banner", {"facing": "south"}, k)
    for x in range(28, 36):        # linteau
        for z in range(0, 3):
            P(x, GROUND + 8, z, "stone_bricks", None, k)
            P(x, GROUND + 9, z, "stone_brick_slab", SLAB(), k)
    for z in range(0, 3):          # forme d'arche
        P(28, GROUND + 7, z, "stone_brick_stairs", STAIRS("west", "top"), k)
        P(35, GROUND + 7, z, "stone_brick_stairs", STAIRS("east", "top"), k)
    for x in (30, 33):
        P(x, GROUND + 7, 1, "lantern", LANTERN(True), k)


for k in range(4):
    arch(k)

# --------------------------------------------------------------------------
# Muret périphérique, piliers à lanterne et haie
# --------------------------------------------------------------------------
for k in range(4):
    for x in range(0, SIZE_X):
        if 25 <= x <= 38:
            continue
        P(x, GROUND + 1, 0, "stone_brick_wall", {}, k)
        if x % 8 == 0 and 0 < x < 63:
            P(x, GROUND + 1, 0, "stone_bricks", None, k)
            P(x, GROUND + 2, 0, "stone_bricks", None, k)
            P(x, GROUND + 3, 0, "lantern", LANTERN(False), k)
        if 2 <= x <= 61 and not 24 <= x <= 39:
            P(x, GROUND + 1, 1, "oak_leaves", LEAVES, k)

# --------------------------------------------------------------------------
# Quartiers : jardins, puits, étals de marché
# --------------------------------------------------------------------------
FLOWERS = ["poppy", "dandelion", "cornflower", "oxeye_daisy", "azure_bluet", "allium", "red_tulip"]


def tree(x, z, h=5):
    for y in range(GROUND + 1, GROUND + 1 + h):
        P(x, y, z, "oak_log", {"axis": "y"})
    top = GROUND + h
    for dy, rad in ((-1, 2), (0, 2), (1, 1), (2, 1)):
        for dx in range(-rad, rad + 1):
            for dz in range(-rad, rad + 1):
                if abs(dx) == rad and abs(dz) == rad and (rad == 1 or rng.random() < .6):
                    continue
                if get(x + dx, top + dy, z + dz) == "minecraft:air":
                    P(x + dx, top + dy, z + dz, "oak_leaves", LEAVES)


def flowerbed(x0, z0, w, d):
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + d):
            if get(x, GROUND + 1, z) == "minecraft:air" and get(x, GROUND, z) == "minecraft:grass_block":
                if rng.random() < .55:
                    P(x, GROUND + 1, z, rng.choice(FLOWERS))
                elif rng.random() < .3:
                    P(x, GROUND + 1, z, "short_grass")


def well(x0, z0):
    for x in range(x0, x0 + 5):
        for z in range(z0, z0 + 5):
            inner = x0 < x < x0 + 4 and z0 < z < z0 + 4
            P(x, GROUND - 1, z, "stone")
            P(x, GROUND, z, "water" if inner else "cobblestone", {"level": "0"} if inner else None)
            if not inner:
                P(x, GROUND + 1, z, "mossy_cobblestone" if rng.random() < .4 else "cobblestone")
            P(x, GROUND + 5, z, "spruce_slab", SLAB())
    for x, z in ((x0, z0), (x0 + 4, z0), (x0, z0 + 4), (x0 + 4, z0 + 4)):
        for y in (GROUND + 2, GROUND + 3, GROUND + 4):
            P(x, y, z, "spruce_fence")
    P(x0 + 2, GROUND + 4, z0 + 2, "lantern", LANTERN(True))


def stall(x0, z0, color, facing):
    """Étal 5x4 : poteaux en sapin, auvent rayé, comptoir et marchandises."""
    for x in range(x0, x0 + 5):
        for z in range(z0, z0 + 4):
            P(x, GROUND, z, "spruce_planks")
            P(x, GROUND + 4, z, f"{color}_wool" if (x - x0) % 2 == 0 else "white_wool")
    for x, z in ((x0, z0), (x0 + 4, z0), (x0, z0 + 3), (x0 + 4, z0 + 3)):
        for y in range(GROUND + 1, GROUND + 4):
            P(x, y, z, "spruce_log", {"axis": "y"})
    front = z0 + 3 if facing == "south" else z0
    back = z0 if facing == "south" else z0 + 3
    for x in range(x0 + 1, x0 + 4):
        P(x, GROUND + 1, front, "spruce_slab", SLAB("top"))
    P(x0 + 1, GROUND + 1, back, "barrel", {"facing": "up", "open": "false"})
    P(x0 + 2, GROUND + 1, back, "crafting_table")
    P(x0 + 3, GROUND + 1, back, "hay_block", {"axis": "y"})
    P(x0 + 2, GROUND + 3, z0 + 1 + (1 if facing == "south" else 0), "lantern", LANTERN(True))


# NO : jardin arboré
tree(8, 8); tree(18, 6); tree(6, 19)
flowerbed(10, 12, 8, 6)
# NE : marché
stall(41, 6, "red", "south"); stall(51, 6, "blue", "south")
stall(41, 17, "yellow", "south")
P(53, GROUND + 1, 18, "barrel", {"facing": "up", "open": "false"}); P(54, GROUND + 1, 18, "hay_block", {"axis": "y"})
# SE : puits et jardin
well(46, 46)
tree(56, 42); tree(42, 56)
flowerbed(50, 53, 8, 6)
# SO : marché et jardin
stall(6, 42, "green", "north"); stall(16, 42, "purple", "north")
tree(8, 55); tree(20, 56)
flowerbed(11, 50, 7, 5)

# --------------------------------------------------------------------------
# Connexions des murets et barrières (calculées une fois tout posé)
# --------------------------------------------------------------------------
SOLID_HINT = ("bricks", "stone", "planks", "log", "cobblestone", "andesite", "wool")


def connects(name, kind):
    if name.endswith("_wall") and kind == "wall":
        return True
    if name.endswith("_fence") and kind == "fence":
        return True
    base = name.split(":")[1]
    return (base.endswith(SOLID_HINT) or base in ("chiseled_stone_bricks", "barrel", "hay_block")) \
        and "slab" not in base and "stairs" not in base


for (x, y, z), (name, props) in list(grid.items()):
    if name.endswith("_wall"):
        sides = {d: connects(get(x + VEC[d][0], y, z + VEC[d][1]), "wall") for d in DIRS}
        for d in DIRS:
            props[d] = "low" if sides[d] else "none"
        straight = (sides["north"] and sides["south"] and not sides["east"] and not sides["west"]) or \
                   (sides["east"] and sides["west"] and not sides["north"] and not sides["south"])
        props["up"] = "false" if straight and get(x, y + 1, z) == "minecraft:air" else "true"
        props["waterlogged"] = "false"
    elif name.endswith("_fence"):
        for d in DIRS:
            props[d] = "true" if connects(get(x + VEC[d][0], y, z + VEC[d][1]), "fence") else "false"
        props["waterlogged"] = "false"


# --------------------------------------------------------------------------
# Écriture NBT (format structure vanilla, gzip)
# --------------------------------------------------------------------------
def nbt_string(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def nbt_payload(tag, value):
    if tag == 3:
        return struct.pack(">i", value)
    if tag == 8:
        return nbt_string(value)
    if tag == 9:
        etag, items = value
        return struct.pack(">bi", etag if items else 0, len(items)) + b"".join(nbt_payload(etag, v) for v in items)
    if tag == 10:
        return b"".join(struct.pack(">b", t) + nbt_string(k) + nbt_payload(t, v) for k, (t, v) in value.items()) + b"\x00"
    raise ValueError(tag)


def write_structure(path):
    palette, index, blocks = [], {}, []
    for y in range(SIZE_Y):
        for z in range(SIZE_Z):
            for x in range(SIZE_X):
                name, props = grid.get((x, y, z), ("minecraft:air", {}))
                key = (name, tuple(sorted(props.items())))
                if key not in index:
                    index[key] = len(palette)
                    entry = {"Name": (8, name)}
                    if props:
                        entry["Properties"] = (10, {k: (8, v) for k, v in sorted(props.items())})
                    palette.append(entry)
                blocks.append({"pos": (9, (3, [x, y, z])), "state": (3, index[key])})
    root = {
        "DataVersion": (3, DATA_VERSION),
        "size": (9, (3, [SIZE_X, SIZE_Y, SIZE_Z])),
        "palette": (9, (10, palette)),
        "blocks": (9, (10, blocks)),
        "entities": (9, (10, [])),
    }
    data = b"\x0a" + nbt_string("") + nbt_payload(10, root)
    path.write_bytes(gzip.compress(data))
    return len(palette), sum(1 for b in grid.values() if b[0] != "minecraft:air")


# --------------------------------------------------------------------------
# Aperçu isométrique (Pillow)
# --------------------------------------------------------------------------
COLORS = {
    "grass_block": (95, 159, 53), "dirt": (134, 96, 67), "stone": (125, 125, 125),
    "stone_bricks": (122, 121, 122), "mossy_stone_bricks": (115, 121, 105), "cracked_stone_bricks": (118, 117, 118),
    "chiseled_stone_bricks": (120, 119, 120), "polished_andesite": (132, 135, 134), "andesite": (136, 136, 137),
    "cobblestone": (127, 127, 127), "mossy_cobblestone": (110, 118, 94), "gravel": (131, 127, 126),
    "stone_brick_slab": (122, 121, 122), "stone_brick_stairs": (122, 121, 122), "stone_brick_wall": (122, 121, 122),
    "water": (63, 118, 228), "lantern": (255, 200, 90), "dark_oak_fence": (66, 43, 20), "spruce_fence": (114, 84, 48),
    "spruce_stairs": (114, 84, 48), "spruce_slab": (114, 84, 48), "spruce_planks": (114, 84, 48), "spruce_log": (58, 37, 16),
    "oak_log": (109, 85, 50), "oak_leaves": (60, 120, 40), "short_grass": (90, 150, 50),
    "red_wall_banner": (170, 30, 30), "barrel": (120, 85, 50), "crafting_table": (140, 100, 60), "hay_block": (200, 170, 40),
    "white_wool": (233, 236, 236), "red_wool": (160, 39, 34), "blue_wool": (53, 57, 157), "yellow_wool": (248, 197, 39),
    "green_wool": (84, 109, 27), "purple_wool": (121, 42, 172),
    "poppy": (200, 30, 30), "dandelion": (240, 220, 40), "cornflower": (70, 110, 230), "oxeye_daisy": (240, 240, 230),
    "azure_bluet": (220, 230, 240), "allium": (180, 100, 220), "red_tulip": (220, 50, 40),
}
SMALL = ("lantern", "fence", "short_grass", "poppy", "dandelion", "cornflower", "daisy", "bluet", "allium", "tulip", "banner")


def render_preview(path, s=7):
    from PIL import Image, ImageDraw
    w = (SIZE_X + SIZE_Z) * s + 40
    ymax = max(y for (x, y, z), (n, _) in grid.items() if n != "minecraft:air")
    y0 = GROUND - 3
    h = (SIZE_X + SIZE_Z) * s // 2 + (ymax + 1 - y0) * s + 40
    img = Image.new("RGB", (w, h), (24, 26, 33))
    d = ImageDraw.Draw(img)
    ox, oy = SIZE_Z * s + 20, (ymax + 1) * s + 20

    def shade(c, f):
        return tuple(max(0, min(255, int(v * f))) for v in c)

    for sm in range(SIZE_X + SIZE_Z):
        for y in range(y0, ymax + 1):
            for x in range(max(0, sm - SIZE_Z + 1), min(SIZE_X, sm + 1)):
                z = sm - x
                name, props = grid.get((x, y, z), ("minecraft:air", {}))
                base = name.split(":")[1]
                if base == "air":
                    continue
                c = COLORS.get(base, (200, 0, 200))
                cx, cy = ox + (x - z) * s, oy + (x + z) * s // 2 - y * s
                hh = s * (0.5 if "slab" in base and props.get("type") != "top" else 1)
                if any(t in base for t in SMALL):
                    r = s // 3 if "lantern" in base or "fence" in base else s // 4
                    d.rectangle([cx - r, cy - s // 2 - r, cx + r, cy - s // 2 + r], fill=c)
                    continue
                top = [(cx, cy - hh - s // 2 + (s - hh)), (cx + s, cy - hh + (s - hh)), (cx, cy - hh + s // 2 + (s - hh)), (cx - s, cy - hh + (s - hh))]
                top = [(px, py) for px, py in top]
                left = [(cx - s, cy - hh + (s - hh)), (cx, cy - hh + s // 2 + (s - hh)), (cx, cy + s // 2), (cx - s, cy)]
                right = [(cx + s, cy - hh + (s - hh)), (cx, cy - hh + s // 2 + (s - hh)), (cx, cy + s // 2), (cx + s, cy)]
                d.polygon(left, fill=shade(c, .72))
                d.polygon(right, fill=shade(c, .55))
                d.polygon(top, fill=c)
    img.save(path)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--no-preview", action="store_true")
    a = ap.parse_args()
    here = Path(__file__).resolve().parent
    n_pal, n_blocks = write_structure(here / "spawn_medieval.nbt")
    print(f"spawn_medieval.nbt : {SIZE_X}x{SIZE_Y}x{SIZE_Z}, {n_blocks} blocs, {n_pal} états de bloc")
    if not a.no_preview:
        render_preview(here / "preview.png")
        print("preview.png généré")
