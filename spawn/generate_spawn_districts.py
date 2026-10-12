#!/usr/bin/env python3
"""Génère le datapack du spawn complet (304 × 304 blocs) autour du spawn médiéval.

Le spawn médiéval de 64 × 64 (generate_spawn.py, déjà posé) reste intact. Autour de lui, sur toute
la safezone (19 × 19 chunks, X -80..223, Z -2256..-1953), ce datapack construit :
  - un plateau mis à niveau (herbe à Y=68, on marche à Y=69) ;
  - un rempart de 3 d'épaisseur, 4 tours d'angle et une seule sortie : la porte PvP au sud ;
  - la place du classement juste derrière la porte ;
  - 4 allées pavées depuis les arches du spawn médiéval ;
  - la salle d'enchantement (ouest), le marché PNJ (est), l'arène de duel (nord) ;
  - la box de minage (sud-ouest), remise à neuf toutes les 10 minutes ;
  - des lampadaires partout (le placement est vérifié par un calcul de lumière).

Usage : python3 spawn/generate_spawn_districts.py   (écrit spawn/datapack/lifecraft_spawn)
Puis sur le serveur : copier le dossier dans world/datapacks/, /reload,
/function lifecraft:spawn/build (puis attendre « Spawn : construction terminée »).

Même architecture que warzone/generate_warzone.py : étapes avec un budget de blocs, forceload de la
zone de l'étape suivante avant de la planifier, fill découpés à 32 768 blocs au plus. Tout est
déterministe (graines fixes) : relancer le générateur ou le build donne le même résultat.
"""
import json
import random
import re
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "datapack" / "lifecraft_spawn"
NS = "lifecraft"
FILL_LIMIT = 32768            # gamerule par défaut : blocs max par commande fill
STEP_BUDGET = 140_000         # blocs modifiés au plus par étape (évite de geler le serveur)
STEP_CHUNKS = 36              # chunks forcés au plus par étape (marge comprise)
STEP_DELAY = "10t"            # délai entre deux étapes : laisse le temps de charger la zone suivante
ENTITY_DELAY = "40t"          # avant l'étape des PNJ : les entités se chargent après les chunks
BOX_PERIOD = "12000t"         # remise à neuf de la box : toutes les 10 minutes

# --------------------------------------------------------------------------------------------
# Géométrie (monde « world », Z négatif = nord). Toutes les bornes sont incluses.
# --------------------------------------------------------------------------------------------
SPAWN = (-80, -2256, 223, -1953)        # emprise du spawn = safezone (19 × 19 chunks)
MED = (45, -2144, 108, -2081)           # spawn médiéval existant : on n'y touche pas
FLOOR = 68                              # herbe du plateau (on marche à Y=69)
G = FLOOR + 1                           # premier bloc au-dessus du sol
AIR_TOP = 110                           # dégagement du relief
DIRT_Y = (60, 67)                       # terre (remplace eau, air, lave)
STONE_Y = (50, 59)                      # sous la terre : on ne comble que les vides

WALL = 3                                # épaisseur du rempart
WALL_TOP = 80                           # rempart de Y=69 à Y=80 (12 de haut)
IN = (SPAWN[0] + WALL, SPAWN[1] + WALL, SPAWN[2] - WALL, SPAWN[3] - WALL)   # intérieur
TOWER = 9                               # tours d'angle 9 × 9
TOWER_TOP = 88

GATE = (69, 75)                         # porte PvP (7 de large), alignée sur le pont sud
GATE_TOP = 77                           # 9 de haut (Y 69..77)
GATEHOUSE = [(63, 67), (77, 81)]        # tours de la porte (X), Z -1960..-1953
GH_Z1 = -1960
GH_TOP = 86

ALLEE_NS = (74, 79)                     # allées nord / sud (X), comme dans le spawn médiéval
ALLEE_EO = (-2115, -2110)               # allées est / ouest (Z)

PLAZA = (62, -1981, 82, -1961)          # place du classement
PODIUM = (72, -1971)                    # centre du podium
HALL = (0, -2125, 24, -2100)            # salle d'enchantement (murs compris)
MARKET = (116, -2126, 180, -2099)       # place du marché
BOX = (-64, -2032, -33, -2001)          # box : 2 × 2 chunks
PIT = (-60, -2028, -37, -2005)          # fosse 24 × 24
PIT_Y = (59, 68)                        # 10 de profondeur, à ras du plateau
ARENA = (64, -2192, 95, -2161)          # arène de duel : 2 × 2 chunks
OLD_KOTH = (77, 64, -2180)              # balise de l'ancienne KOTH (supprimée)

# Vérifie que la porte est dans l'axe du pont sud de la warzone
sys.dont_write_bytecode = True           # pas de __pycache__ dans warzone/
sys.path.insert(0, str(HERE.parent / "warzone"))
import generate_warzone as wz           # noqa: E402  (aucun effet de bord : main() est protégé)

assert tuple(wz.SPAWN) == SPAWN, "l'emprise du spawn ne correspond plus à celle de la warzone"
_sud = next(b for b in wz.BRIDGES if b[0] == "sud")
assert (GATE[0] + GATE[1]) // 2 == _sud[2] and GATE[1] - GATE[0] == 6, \
    f"porte PvP {GATE} pas centrée sur le pont sud (X={_sud[2]})"


# --------------------------------------------------------------------------------------------
# Outils (repris de warzone/generate_warzone.py)
# --------------------------------------------------------------------------------------------
def chunks_of(area, margin=1):
    """Chunks (cx, cz) couverts par une zone (x1, z1, x2, z2), avec une marge en blocs."""
    x1, z1, x2, z2 = area
    x1, z1, x2, z2 = x1 - margin, z1 - margin, x2 + margin, z2 + margin
    return {(cx, cz) for cx in range(x1 >> 4, (x2 >> 4) + 1) for cz in range(z1 >> 4, (z2 >> 4) + 1)}


def union(a, b):
    return (min(a[0], b[0]), min(a[1], b[1]), max(a[2], b[2]), max(a[3], b[3]))


class Job:
    """Liste de commandes regroupées en étapes, chacune avec la zone à charger.

    Deux ajouts consécutifs sont fusionnés si la zone commune reste petite (STEP_CHUNKS) et si le
    volume reste dans le budget. Une étape « seule » n'est jamais fusionnée (PNJ, box).
    """

    def __init__(self):
        self.steps = []          # dicts : area, cmds, vol, delay, alone, labels

    def add(self, area, cmds, vol=None, label=None, alone=False, delay=None):
        cmds = list(cmds)
        vol = len(cmds) if vol is None else vol
        if area is None:                         # message : rattaché à l'étape précédente
            self.steps[-1]["cmds"].extend(cmds)
            return
        last = self.steps[-1] if self.steps else None
        if last and not alone and not last["alone"]:
            u = union(last["area"], area)
            if len(chunks_of(u)) <= STEP_CHUNKS and last["vol"] + vol <= STEP_BUDGET:
                last["area"] = u
                last["cmds"].extend(cmds)
                last["vol"] += vol
                if label and label not in last["labels"]:
                    last["labels"].append(label)
                return
        self.steps.append({"area": area, "cmds": cmds, "vol": vol, "alone": alone,
                           "delay": delay or STEP_DELAY, "labels": [label] if label else []})

    def say(self, text):
        self.add(None, [f"say {text}"])


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


def vol_of(b):
    return (b[3] - b[0] + 1) * (b[4] - b[1] + 1) * (b[5] - b[2] + 1)


def fills(box, block, mode=""):
    """Commandes fill (découpées) pour une boîte quelconque."""
    return [f"fill {b[0]} {b[1]} {b[2]} {b[3]} {b[4]} {b[5]} {block}{' ' + mode if mode else ''}"
            for b in split_box(*box)]


class Build:
    """Accumule les commandes d'une partie (zone + volume) avant de les donner au Job."""

    def __init__(self, area):
        self.area = area
        self.cmds = []
        self.vol = 0

    def fill(self, x1, y1, z1, x2, y2, z2, block, mode="", weight=1.0):
        c = fills((x1, y1, z1, x2, y2, z2), block, mode)
        self.cmds += c
        self.vol += int(vol_of((min(x1, x2), min(y1, y2), min(z1, z2), max(x1, x2), max(y1, y2), max(z1, z2))) * weight)

    def set(self, x, y, z, block):
        self.cmds.append(f"setblock {x} {y} {z} {block}")
        self.vol += 1

    def raw(self, *cmds):
        self.cmds += cmds
        self.vol += len(cmds)


def subtract(rect, hole):
    """Rectangle (x1, z1, x2, z2) privé d'un autre : jusqu'à 4 rectangles."""
    x1, z1, x2, z2 = rect
    hx1, hz1, hx2, hz2 = hole
    if hx1 > x2 or hx2 < x1 or hz1 > z2 or hz2 < z1:
        return [rect]
    out = []
    if z1 < hz1:
        out.append((x1, z1, x2, hz1 - 1))
    if z2 > hz2:
        out.append((x1, hz2 + 1, x2, z2))
    mz1, mz2 = max(z1, hz1), min(z2, hz2)
    if x1 < hx1:
        out.append((x1, mz1, hx1 - 1, mz2))
    if x2 > hx2:
        out.append((hx2 + 1, mz1, x2, mz2))
    return out


def inside(x, z, r):
    return r[0] <= x <= r[2] and r[1] <= z <= r[3]


# --------------------------------------------------------------------------------------------
# Plan au sol (Y=69) : sert au calcul de lumière et au placement automatique des lampadaires
# --------------------------------------------------------------------------------------------
FREE, EDGE, ZONE, OBST, MEDC = 0, 1, 2, 3, 4      # libre / bord / quartier / obstacle / médiéval
PX1, PZ1, PX2, PZ2 = IN
PW, PD = PX2 - PX1 + 1, PZ2 - PZ1 + 1
plan = [[FREE] * PD for _ in range(PW)]
lights = []                                        # (x, y, z) des sources posées près du sol


def mark(rect, cls):
    x1, z1, x2, z2 = rect
    for x in range(max(x1, PX1), min(x2, PX2) + 1):
        col = plan[x - PX1]
        for z in range(max(z1, PZ1), min(z2, PZ2) + 1):
            if cls > col[z - PZ1] or cls == MEDC:
                col[z - PZ1] = cls


def light(b, x, y, z, block="minecraft:lantern"):
    b.set(x, y, z, block)
    lights.append((x, y, z))


def lamp_post(b, x, z, h=3):
    """Lampadaire du spawn médiéval : 3 barrières en chêne noir et une lanterne."""
    b.fill(x, G, z, x, G + h - 1, z, "minecraft:dark_oak_fence")
    light(b, x, G + h, z)


# --------------------------------------------------------------------------------------------
# 1. Plateau
# --------------------------------------------------------------------------------------------
def build_plateau(job):
    """Mise à niveau par tuiles de 2 × 2 chunks (l'emprise est alignée sur les chunks)."""
    x0, z0, x3, z3 = SPAWN
    for tz in range(z0, z3 + 1, 32):
        for tx in range(x0, x3 + 1, 32):
            tile = (tx, tz, min(tx + 31, x3), min(tz + 31, z3))
            for (x1, z1, x2, z2) in subtract(tile, MED):
                b = Build((x1, z1, x2, z2))
                # du bas vers le haut : l'eau est remplacée avant que l'on dégage au-dessus
                b.fill(x1, STONE_Y[0], z1, x2, STONE_Y[1], z2, "minecraft:stone", "replace #minecraft:replaceable")
                b.fill(x1, DIRT_Y[0], z1, x2, DIRT_Y[1], z2, "minecraft:dirt", "replace #minecraft:replaceable")
                b.fill(x1, FLOOR, z1, x2, FLOOR, z2, "minecraft:grass_block")
                b.fill(x1, G, z1, x2, AIR_TOP, z2, "minecraft:air")
                job.add(b.area, b.cmds, b.vol, "plateau")
    job.say("Spawn : plateau nivelé, nettoyage de l'eau...")
    # 2e passage : l'eau d'une tuile voisine a pu couler sur le plateau pendant la construction
    for tz in range(z0, z3 + 1, 64):
        for tx in range(x0, x3 + 1, 64):
            tile = (tx, tz, min(tx + 63, x3), min(tz + 63, z3))
            for (x1, z1, x2, z2) in subtract(tile, MED):
                b = Build((x1, z1, x2, z2))
                b.fill(x1, G, z1, x2, G + 5, z2, "minecraft:air", "replace minecraft:water", weight=0.1)
                b.fill(x1, G, z1, x2, G + 5, z2, "minecraft:air", "replace minecraft:lava", weight=0.1)
                job.add(b.area, b.cmds, b.vol, "eau")
    job.say("Spawn : plateau terminé, construction du rempart...")


# --------------------------------------------------------------------------------------------
# 2. Rempart, tours d'angle, porte PvP
# --------------------------------------------------------------------------------------------
def build_rampart(job):
    x0, z0, x3, z3 = SPAWN
    rng = random.Random(1204)
    # côtés : (rectangle, axe de la longueur, rangée extérieure, rangée intérieure)
    sides = [
        ((x0, z0, x3, z0 + 2), "x", z0, z0 + 2),
        ((x0, z3 - 2, x3, z3), "x", z3, z3 - 2),
        ((x0, z0 + 3, x0 + 2, z3 - 3), "z", x0, x0 + 2),
        ((x3 - 2, z0 + 3, x3, z3 - 3), "z", x3, x3 - 2),
    ]
    for (rx1, rz1, rx2, rz2), axis, outer, inner in sides:
        lo, hi = (rx1, rx2) if axis == "x" else (rz1, rz2)
        for a in range(lo, hi + 1, 32):
            c = min(a + 31, hi)
            seg = (a, rz1, c, rz2) if axis == "x" else (rx1, a, rx2, c)
            b = Build(seg)
            sx1, sz1, sx2, sz2 = seg
            b.fill(sx1, 56, sz1, sx2, WALL_TOP, sz2, "minecraft:stone_bricks")
            if axis == "x":
                row = lambda r, u1, u2, y, blk: b.fill(u1, y, r, u2, y, r, blk)
                at = lambda r, u, y, blk: b.set(u, y, r, blk)
            else:
                row = lambda r, u1, u2, y, blk: b.fill(r, y, u1, r, y, u2, blk)
                at = lambda r, u, y, blk: b.set(r, y, u, blk)
            # chemin de ronde : parapet et créneaux dehors, garde-corps et lanternes dedans
            row(outer, a, c, WALL_TOP + 1, "minecraft:stone_bricks")
            row(inner, a, c, WALL_TOP + 1, "minecraft:stone_brick_wall")
            for u in range(a, c + 1):
                if u % 2 == 0:
                    at(outer, u, WALL_TOP + 2, "minecraft:stone_bricks")
                if u % 8 == 4:
                    at(inner, u, WALL_TOP + 2, "minecraft:lantern")
                # pierres moussues / fissurées sur les deux faces (déterministe)
                for y in range(G, WALL_TOP + 1):
                    for r in (outer, inner):
                        p = rng.random()
                        if p < 0.025:
                            at(r, u, y, "minecraft:mossy_stone_bricks")
                        elif p < 0.04:
                            at(r, u, y, "minecraft:cracked_stone_bricks")
            job.add(seg, b.cmds, b.vol, "rempart")

    # tours d'angle 9 × 9, plus hautes, créneaux et lanternes au sommet
    for tx, tz in ((x0, z0), (x3 - TOWER + 1, z0), (x0, z3 - TOWER + 1), (x3 - TOWER + 1, z3 - TOWER + 1)):
        area = (tx, tz, tx + TOWER - 1, tz + TOWER - 1)
        b = Build(area)
        tower(b, area, TOWER_TOP)
        job.add(area, b.cmds, b.vol, "tours")
    job.say("Spawn : rempart et tours posés, construction de la porte PvP...")

    # porte PvP : deux tours, passage de 7 × 9, herse décorative et bannières rouges
    area = (GATEHOUSE[0][0], GH_Z1, GATEHOUSE[1][1], z3)
    b = Build(area)
    for gx1, gx2 in GATEHOUSE:
        tower(b, (gx1, GH_Z1, gx2, z3), GH_TOP)
    gx1, gx2 = GATE
    b.fill(gx1, G, z3 - 2, gx2, GATE_TOP, z3, "minecraft:air")
    b.fill(gx1 - 1, FLOOR, GH_Z1, gx2 + 1, FLOOR, z3, "minecraft:polished_andesite")
    b.fill(gx1 - 1, GATE_TOP + 1, z3 - 2, gx2 + 1, GATE_TOP + 1, z3, "minecraft:chiseled_stone_bricks")
    b.fill(gx1, GATE_TOP, z3 - 1, gx2, GATE_TOP, z3 - 1, "minecraft:iron_bars")       # herse
    for x in range(gx1, gx2 + 1, 2):
        b.set(x, GATE_TOP - 1, z3 - 1, "minecraft:iron_bars")
    b.set(gx1, GATE_TOP - 2, z3, "minecraft:red_wall_banner[facing=east]")           # vers la warzone
    b.set(gx2, GATE_TOP - 2, z3, "minecraft:red_wall_banner[facing=west]")
    for gx1_, gx2_ in GATEHOUSE:                                                       # vers le spawn
        b.set((gx1_ + gx2_) // 2, GATE_TOP + 1, GH_Z1 - 1, "minecraft:red_wall_banner[facing=north]")
    lamp_post(b, gx1 - 1, z3 - 4); lamp_post(b, gx2 + 1, z3 - 4)                     # de part et d'autre du passage
    job.add(area, b.cmds, b.vol, "porte")
    mark((GATEHOUSE[0][0], GH_Z1, GATEHOUSE[0][1], z3), OBST)
    mark((GATEHOUSE[1][0], GH_Z1, GATEHOUSE[1][1], z3), OBST)
    mark((GATE[0] - 1, GH_Z1, GATE[1] + 1, z3), ZONE)
    for (tx, tz) in ((x0, z0), (x3 - TOWER + 1, z0), (x0, z3 - TOWER + 1), (x3 - TOWER + 1, z3 - TOWER + 1)):
        mark((tx, tz, tx + TOWER - 1, tz + TOWER - 1), OBST)


def tower(b, area, top):
    """Tour pleine en pierre taillée : parapet, créneaux, lanternes aux coins du sommet."""
    x1, z1, x2, z2 = area
    b.fill(x1, 56, z1, x2, top, z2, "minecraft:stone_bricks")
    b.fill(x1, WALL_TOP + 1, z1, x2, WALL_TOP + 1, z2, "minecraft:chiseled_stone_bricks")   # bandeau
    b.fill(x1, WALL_TOP + 2, z1, x2, top, z2, "minecraft:stone_bricks")
    b.fill(x1, top + 1, z1, x2, top + 1, z2, "minecraft:stone_bricks")
    b.fill(x1 + 1, top + 1, z1 + 1, x2 - 1, top + 1, z2 - 1, "minecraft:air")
    for x in range(x1, x2 + 1):
        for z in (z1, z2):
            if (x + z) % 2 == 0:
                b.set(x, top + 2, z, "minecraft:stone_bricks")
    for z in range(z1 + 1, z2):
        for x in (x1, x2):
            if (x + z) % 2 == 0:
                b.set(x, top + 2, z, "minecraft:stone_bricks")
    for x, z in ((x1 + 1, z1 + 1), (x2 - 1, z1 + 1), (x1 + 1, z2 - 1), (x2 - 1, z2 - 1)):
        b.set(x, top + 1, z, "minecraft:lantern")


# --------------------------------------------------------------------------------------------
# 3. Allées pavées et 4. place du classement
# --------------------------------------------------------------------------------------------
def pave(b, rect, axis, rng):
    """Allée : bordures en pierre taillée, cœur en pavés variés (comme le spawn médiéval)."""
    x1, z1, x2, z2 = rect
    b.fill(x1, FLOOR, z1, x2, FLOOR, z2, "minecraft:cobblestone")
    if axis == "z":       # allée nord-sud : bordures à l'ouest et à l'est
        b.fill(x1, FLOOR, z1, x1, FLOOR, z2, "minecraft:stone_bricks")
        b.fill(x2, FLOOR, z1, x2, FLOOR, z2, "minecraft:stone_bricks")
        core = [(x, z) for z in range(z1, z2 + 1) for x in range(x1 + 1, x2)]
    else:
        b.fill(x1, FLOOR, z1, x2, FLOOR, z1, "minecraft:stone_bricks")
        b.fill(x1, FLOOR, z2, x2, FLOOR, z2, "minecraft:stone_bricks")
        core = [(x, z) for x in range(x1, x2 + 1) for z in range(z1 + 1, z2)]
    for x, z in core:
        r = rng.random()
        if r >= 0.5:
            blk = "mossy_cobblestone" if r < .7 else "andesite" if r < .9 else "gravel"
            b.set(x, FLOOR, z, "minecraft:" + blk)
    mark(rect, ZONE)


def allee(job, rect, axis, name, seed):
    """Allée et ses lampadaires tous les 12 blocs, de chaque côté."""
    rng = random.Random(seed)
    x1, z1, x2, z2 = rect
    area = (x1 - 1, z1 - 1, x2 + 1, z2 + 1)
    b = Build(area)
    pave(b, rect, axis, rng)
    if axis == "z":
        for z in range(z1 + 2, z2 + 1, 12):
            lamp_post(b, x1 - 1, z); lamp_post(b, x2 + 1, z)
    else:
        for x in range(x1 + 2, x2 + 1, 12):
            lamp_post(b, x, z1 - 1); lamp_post(b, x, z2 + 1)
    mark(area, ZONE)
    job.add(area, b.cmds, b.vol, f"allée {name}")


def build_allees(job):
    ax1, ax2 = ALLEE_NS
    az1, az2 = ALLEE_EO
    allee(job, (ax1, MED[3] + 1, ax2, PLAZA[1] - 1), "z", "sud", 31)         # vers la porte
    allee(job, (ax1, ARENA[3] + 1, ax2, MED[1] - 1), "z", "nord", 32)        # vers l'arène
    allee(job, (HALL[2] + 1, az1, MED[0] - 1, az2), "x", "ouest", 33)        # vers l'enchantement
    allee(job, (MED[2] + 1, az1, MARKET[0] - 1, az2), "x", "est", 34)        # vers le marché


def build_plaza(job):
    x1, z1, x2, z2 = PLAZA
    px, pz = PODIUM
    area = (x1, z1, x2, GH_Z1)
    b = Build(area)
    b.fill(x1, FLOOR, z1, x2, FLOOR, z2, "minecraft:stone_bricks")
    b.fill(x1, FLOOR, z1, x2, FLOOR, z1, "minecraft:polished_andesite")
    b.fill(x1, FLOOR, z2, x2, FLOOR, z2, "minecraft:polished_andesite")
    b.fill(x1, FLOOR, z1, x1, FLOOR, z2, "minecraft:polished_andesite")
    b.fill(x2, FLOOR, z1, x2, FLOOR, z2, "minecraft:polished_andesite")
    b.fill(px - 5, FLOOR, pz - 5, px + 5, FLOOR, pz + 5, "minecraft:polished_andesite")
    b.fill(px - 4, FLOOR, pz - 4, px + 4, FLOOR, pz + 4, "minecraft:chiseled_stone_bricks")
    b.fill(px - 3, FLOOR, pz - 3, px + 3, FLOOR, pz + 3, "minecraft:stone_bricks")
    # liaison avec la porte, entre les deux tours
    b.fill(GATE[0] - 1, FLOOR, z2 + 1, GATE[1] + 1, FLOOR, GH_Z1, "minecraft:polished_andesite")
    # podium : socle 5 × 5, gradin 3 × 3, pierre centrale ; le classement flotte 3 blocs au-dessus
    b.fill(px - 2, G, pz - 2, px + 2, G, pz + 2, "minecraft:smooth_stone_slab")
    b.fill(px - 1, G, pz - 1, px + 1, G, pz + 1, "minecraft:polished_blackstone_bricks")
    b.set(px, G + 1, pz, "minecraft:chiseled_polished_blackstone")
    for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        b.set(px + dx, G, pz + dz, "minecraft:polished_blackstone_bricks")
        light(b, px + dx, G + 1, pz + dz)
    # bancs face au podium
    for z in (pz - 6, pz - 5, pz + 5, pz + 6):
        b.set(x1 + 2, G, z, "minecraft:spruce_stairs[facing=west]")
        b.set(x2 - 2, G, z, "minecraft:spruce_stairs[facing=east]")
    # lampadaires aux coins et au milieu des côtés
    for x, z in ((x1 + 1, z1 + 1), (x2 - 1, z1 + 1), (x1 + 1, z2 - 1), (x2 - 1, z2 - 1),
                 (x1 + 1, pz), (x2 - 1, pz), (70, z1 + 1), (66, z2 - 1), (78, z2 - 1)):
        lamp_post(b, x, z)
    mark((x1 - 1, z1 - 1, x2 + 1, z2), ZONE)
    mark((px - 2, pz - 2, px + 2, pz + 2), OBST)
    job.add(area, b.cmds, b.vol, "place du classement")


# --------------------------------------------------------------------------------------------
# 5. Salle d'enchantement (ouest)
# --------------------------------------------------------------------------------------------
TABLES = [(6, -2118), (18, -2118)]
ANVILS = [(5, HALL[3] - 2), (9, HALL[3] - 2)]


def build_hall(job):
    x1, z1, x2, z2 = HALL
    area = (x1 - 1, z1 - 1, x2 + 1, z2 + 1)
    b = Build(area)
    zc1, zc2 = -2113, -2112                                   # porte double, dans l'axe de l'allée
    # sol, murs, bandeau, fenêtres
    b.fill(x1, 56, z1, x2, FLOOR, z2, "minecraft:stone_bricks")
    b.fill(x1 + 1, FLOOR, z1 + 1, x2 - 1, FLOOR, z2 - 1, "minecraft:spruce_planks")
    b.fill(x1, G, z1, x2, G + 6, z2, "minecraft:stone_bricks")
    b.fill(x1 + 1, G, z1 + 1, x2 - 1, G + 20, z2 - 1, "minecraft:air")
    b.fill(x1, G + 6, z1, x2, G + 6, z2, "minecraft:chiseled_stone_bricks")
    b.fill(x1 + 1, G + 6, z1 + 1, x2 - 1, G + 6, z2 - 1, "minecraft:air")
    for x, z in ((x1, z1), (x2, z1), (x1, z2), (x2, z2)):
        b.fill(x, G, z, x, G + 6, z, "minecraft:spruce_log")
    for x in (3, 4, 9, 10, 14, 15, 20, 21):
        for z in (z1, z2):
            b.fill(x, G + 2, z, x, G + 3, z, "minecraft:glass_pane")
    for z in (-2120, -2119, -2106, -2105):
        for x in (x1, x2):
            b.fill(x, G + 2, z, x, G + 3, z, "minecraft:glass_pane")
    # toit à deux pans (faîtage est-ouest), pignons en planches
    for i in range(13):
        y = G + 6 + i
        b.fill(x1 - 1, y, z1 - 1 + i, x2 + 1, y, z1 - 1 + i, "minecraft:spruce_stairs[facing=south]")
        b.fill(x1 - 1, y, z2 + 1 - i, x2 + 1, y, z2 + 1 - i, "minecraft:spruce_stairs[facing=north]")
    b.fill(x1 - 1, G + 19, z1 + 12, x2 + 1, G + 19, z2 - 12, "minecraft:spruce_slab")
    for z in range(z1, z2 + 1):
        i = min(z - (z1 - 1), (z2 + 1) - z)
        if i >= 2:
            for x in (x1, x2):
                b.fill(x, G + 7, z, x, G + 5 + i, z, "minecraft:spruce_planks")
    # porte double à l'est, bannières de part et d'autre
    b.fill(x2, G, zc1, x2, G + 2, zc2, "minecraft:air")
    b.set(x2, G + 2, zc1, "minecraft:stone_bricks"); b.set(x2, G + 2, zc2, "minecraft:stone_bricks")
    b.set(x2, G, zc1, "minecraft:oak_door[facing=west,half=lower,hinge=right,open=false]")
    b.set(x2, G + 1, zc1, "minecraft:oak_door[facing=west,half=upper,hinge=right,open=false]")
    b.set(x2, G, zc2, "minecraft:oak_door[facing=west,half=lower,hinge=left,open=false]")
    b.set(x2, G + 1, zc2, "minecraft:oak_door[facing=west,half=upper,hinge=left,open=false]")
    b.set(x2 + 1, G + 3, zc1 - 1, "minecraft:purple_wall_banner[facing=east]")
    b.set(x2 + 1, G + 3, zc2 + 1, "minecraft:purple_wall_banner[facing=east]")
    # tables d'enchantement : 15 bibliothèques en anneau à 2 blocs, ouverture au sud, air entre les deux
    for tx, tz in TABLES:
        b.fill(tx - 2, G, tz - 2, tx + 2, G + 1, tz + 2, "minecraft:air")
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if max(abs(dx), abs(dz)) == 2 and (dx, dz) != (0, 2):
                    b.set(tx + dx, G, tz + dz, "minecraft:bookshelf")
        b.set(tx, G, tz, "minecraft:enchanting_table")
        for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
            light(b, tx + dx, G + 1, tz + dz)
        b.fill(tx - 1, FLOOR, tz - 1, tx + 1, FLOOR, tz + 1, "minecraft:polished_deepslate")
    # tapis de l'entrée aux tables
    b.fill(TABLES[0][0], G, zc1, x2 - 1, G, zc2, "minecraft:red_carpet")
    b.fill(TABLES[0][0], G, -2116, TABLES[0][0], G, zc1 - 1, "minecraft:red_carpet")
    b.fill(TABLES[1][0], G, -2116, TABLES[1][0], G, zc1 - 1, "minecraft:red_carpet")
    # enclumes et meule le long du mur sud
    for x, z in ANVILS:
        b.set(x, G, z, "minecraft:anvil[facing=north]")
    b.set(18, G, z2 - 2, "minecraft:grindstone[face=floor,facing=north]")
    b.set(15, G, z2 - 1, "minecraft:lectern[facing=north]")
    # éclairage : lanternes sur poteaux le long des murs
    for x, z in ((x1 + 1, z1 + 1), (x2 - 1, z1 + 1), (x1 + 1, z2 - 1), (x2 - 1, z2 - 1),
                 (12, z1 + 1), (12, z2 - 1), (x1 + 1, -2112), (12, -2108), (x2 - 1, -2108), (x2 - 1, -2117)):
        b.set(x, G, z, "minecraft:spruce_fence")
        light(b, x, G + 1, z)
    # lanternes dehors, au pied des murs
    for x, z in ((x2 + 1, zc1 - 3), (x2 + 1, zc2 + 3), (x1 - 1, -2112), (12, z1 - 1), (12, z2 + 1)):
        light(b, x, G, z)
    job.add(area, b.cmds, b.vol, "salle d'enchantement")
    mark(area, ZONE)
    for r in ((x1, z1, x2, z1), (x1, z2, x2, z2), (x1, z1, x1, z2), (x2, z1, x2, z2)):
        mark(r, OBST)
    mark((x2, zc1, x2, zc2), ZONE)
    for tx, tz in TABLES:
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if max(abs(dx), abs(dz)) == 2 and (dx, dz) != (0, 2):
                    mark((tx + dx, tz + dz, tx + dx, tz + dz), OBST)


# --------------------------------------------------------------------------------------------
# 6. Marché PNJ (est)
# --------------------------------------------------------------------------------------------
def item(id_, count=1, comps=None):
    s = f'{{id:"minecraft:{id_}",count:{count}'
    if comps:
        s += f",components:{{{comps}}}"
    return s + "}"


def book(ench, lvl):
    return item("enchanted_book", 1, f'"minecraft:stored_enchantments":{{"minecraft:{ench}":{lvl}}}')


def trade(buy, sell, buy_b=None):
    s = f"{{buy:{buy},"
    if buy_b:
        s += f"buyB:{buy_b},"
    return s + f"sell:{sell},maxUses:2147483647,rewardExp:0b,xp:0,priceMultiplier:0.0f}}"


E = lambda n: item("emerald", n)
# (nom, couleur, métier, bloc de métier, couleur d'étal, échanges)
MERCHANTS = [
    ("Armurier", "gray", "armorer", "minecraft:blast_furnace[facing=south]", "red", [
        trade(E(10), item("iron_helmet")), trade(E(16), item("iron_chestplate")),
        trade(E(14), item("iron_leggings")), trade(E(9), item("iron_boots")),
        trade(E(5), item("shield")), trade(item("iron_ingot", 8), E(1))]),
    ("Forgeron", "dark_red", "weaponsmith", "minecraft:grindstone[face=floor,facing=south]", "blue", [
        trade(E(6), item("iron_sword")), trade(E(8), item("iron_axe")),
        trade(E(24), item("diamond_sword")), trade(E(4), item("bow")),
        trade(E(1), item("arrow", 16)), trade(item("coal", 16), E(1))]),
    ("Fermier", "green", "farmer", "minecraft:composter", "yellow", [
        trade(E(1), item("bread", 6)), trade(E(1), item("apple", 4)),
        trade(E(3), item("golden_carrot", 4)), trade(E(1), item("pumpkin_pie", 4)),
        trade(item("wheat", 20), E(1)), trade(item("carrot", 16), E(1))]),
    ("Bibliothécaire", "light_purple", "librarian", "minecraft:lectern[facing=south]", "purple", [
        trade(E(14), book("sharpness", 3), item("book")), trade(E(14), book("protection", 3), item("book")),
        trade(E(12), book("efficiency", 3), item("book")), trade(E(12), book("unbreaking", 3), item("book")),
        trade(E(10), book("feather_falling", 4), item("book")), trade(E(12), book("power", 3), item("book")),
        trade(E(20), book("fortune", 2), item("book")), trade(item("paper", 24), E(1))]),
    ("Maçon", "gold", "mason", "minecraft:stonecutter[facing=south]", "green", [
        trade(E(1), item("stone_bricks", 16)), trade(E(1), item("polished_andesite", 16)),
        trade(E(2), item("terracotta", 16)), trade(E(3), item("quartz_block", 8)),
        trade(E(1), item("glass", 16)), trade(item("clay_ball", 10), E(1))]),
    ("Boucher", "red", "butcher", "minecraft:smoker[facing=south]", "orange", [
        trade(E(1), item("cooked_beef", 6)), trade(E(1), item("cooked_porkchop", 6)),
        trade(E(1), item("cooked_chicken", 8)), trade(item("chicken", 14), E(1)),
        trade(item("porkchop", 7), E(1))]),
    ("Outilleur", "aqua", "toolsmith", "minecraft:smithing_table", "cyan", [
        trade(E(6), item("iron_pickaxe")), trade(E(4), item("iron_shovel")),
        trade(E(22), item("diamond_pickaxe")), trade(E(10), item("diamond_shovel")),
        trade(E(2), item("shears")), trade(item("iron_ingot", 6), E(1))]),
    ("Clerc", "dark_purple", "cleric", "minecraft:brewing_stand", "lime", [
        trade(E(6), item("ender_pearl")), trade(E(1), item("redstone", 4)),
        trade(E(1), item("lapis_lazuli", 4)), trade(E(3), item("experience_bottle", 4)),
        trade(E(2), item("glowstone", 4)), trade(item("rotten_flesh", 32), E(1))]),
]
STALLS_X = [122, 134, 146, 158]
STALL_Z = {"south": -2122, "north": -2106}     # étals au nord de la rue (ouverts au sud) et au sud


def villager(x, z, yaw, name, color, prof, trades):
    nbt = ("{NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Silent:1b,CustomNameVisible:1b,"
           f'CustomName:{{text:"{name}",color:"{color}"}},Tags:["lifecraft_pnj"],Rotation:[{yaw}f,0f],'
           f'VillagerData:{{profession:"minecraft:{prof}",level:5,type:"minecraft:plains"}},Xp:250,'
           f"Offers:{{Recipes:[{','.join(trades)}]}}}}")
    return f"summon minecraft:villager {x} {G} {z} {nbt}"


def build_market(job):
    x1, z1, x2, z2 = MARKET
    area = (x1 - 1, z1 - 1, x2 + 1, z2 + 1)
    b = Build(area)
    rng = random.Random(4242)
    # place pavée, bordure en andésite polie
    b.fill(x1, FLOOR, z1, x2, FLOOR, z2, "minecraft:cobblestone")
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            r = rng.random()
            if r >= 0.55:
                b.set(x, FLOOR, z, "minecraft:" + ("mossy_cobblestone" if r < .72 else "andesite" if r < .9 else "stone_bricks"))
    for r in ((x1, z1, x2, z1), (x1, z2, x2, z2), (x1, z1, x1, z2), (x2, z1, x2, z2)):
        b.fill(r[0], FLOOR, r[1], r[2], FLOOR, r[3], "minecraft:polished_andesite")
    mark(area, ZONE)
    # étals (même modèle que ceux du spawn médiéval) et leur marchand
    b.raw("# PNJ : on retire d'abord ceux d'une construction précédente (pas de doublons)",
          "minecraft:kill @e[type=minecraft:villager,tag=lifecraft_pnj]")
    summons = []
    k = 0
    for facing in ("south", "north"):
        for sx in STALLS_X:
            name, color, prof, job_block, wool, trades = MERCHANTS[k]
            k += 1
            z0 = STALL_Z[facing]
            for dx in range(5):
                b.fill(sx + dx, G + 3, z0, sx + dx, G + 3, z0 + 3, f"minecraft:{wool if dx % 2 == 0 else 'white'}_wool")
            b.fill(sx, FLOOR, z0, sx + 4, FLOOR, z0 + 3, "minecraft:spruce_planks")
            for px, pz in ((sx, z0), (sx + 4, z0), (sx, z0 + 3), (sx + 4, z0 + 3)):
                b.fill(px, G, pz, px, G + 2, pz, "minecraft:spruce_log")
                mark((px, pz, px, pz), OBST)
            front = z0 + 3 if facing == "south" else z0
            back = z0 if facing == "south" else z0 + 3
            b.fill(sx + 1, G, front, sx + 3, G, front, "minecraft:spruce_slab[type=top]")
            b.set(sx + 1, G, back, "minecraft:barrel[facing=up]")
            jb = job_block if facing == "south" else job_block.replace("facing=south", "facing=north")
            b.set(sx + 2, G, back, jb)
            b.set(sx + 3, G, back, "minecraft:hay_block")
            lz = z0 + 2 if facing == "south" else z0 + 1
            light(b, sx + 2, G + 2, lz, "minecraft:lantern[hanging=true]")
            vz = z0 + 2.5 if facing == "south" else z0 + 1.5
            summons.append(villager(sx + 2.5, vz, 0 if facing == "south" else 180, name, color, prof, trades))
    b.raw(*summons)
    # puits au bout de la rue
    wx, wz = 168, -2115
    b.fill(wx, FLOOR - 1, wz, wx + 4, FLOOR - 1, wz + 4, "minecraft:stone")
    b.fill(wx, FLOOR, wz, wx + 4, G, wz + 4, "minecraft:cobblestone")
    b.fill(wx + 1, FLOOR, wz + 1, wx + 3, G, wz + 3, "minecraft:air")
    b.fill(wx + 1, FLOOR, wz + 1, wx + 3, FLOOR, wz + 3, "minecraft:water")
    for px, pz in ((wx, wz), (wx + 4, wz), (wx, wz + 4), (wx + 4, wz + 4)):
        b.fill(px, G + 1, pz, px, G + 3, pz, "minecraft:spruce_fence")
    b.fill(wx, G + 4, wz, wx + 4, G + 4, wz + 4, "minecraft:spruce_slab")
    light(b, wx + 2, G + 3, wz + 2, "minecraft:lantern[hanging=true]")
    mark((wx, wz, wx + 4, wz + 4), OBST)
    # lampadaires : entre les étals, aux bouts et le long des bords
    for x in [sx - 3 for sx in STALLS_X] + [STALLS_X[-1] + 8, x2 - 2]:
        for z in (-2120, -2105):
            lamp_post(b, x, z)
    for x in range(x1 + 4, x2, 10):
        lamp_post(b, x, z1 + 1); lamp_post(b, x, z2 - 1)
    job.add(area, b.cmds, b.vol, "marché", alone=True, delay=ENTITY_DELAY)


# --------------------------------------------------------------------------------------------
# 7. Box de minage (sud-ouest) : structure + fonction de remise à neuf
# --------------------------------------------------------------------------------------------
STAIR_X = (PIT[0], PIT[0] + 1)                 # escalier d'accès contre le mur ouest de la fosse
STAIR_Z0 = PIT[1]                              # en haut, côté nord
STAIR_N = PIT_Y[1] - PIT_Y[0]                  # 9 marches : Y 67 → 59
LANDING = (STAIR_Z0 + STAIR_N, STAIR_Z0 + STAIR_N + 1)
ORE_RECTS = [(PIT[0] + 2, PIT[1], PIT[2], PIT[3]),            # fosse sans la bande de l'escalier
             (PIT[0], LANDING[1] + 1, PIT[0] + 1, PIT[3])]    # bande de l'escalier, après le palier
WX1, WZ1, WX2, WZ2 = PIT[0] - 1, PIT[1] - 1, PIT[2] + 1, PIT[3] + 1   # parois


def ore_layout():
    """Filons déterministes (graine fixe). Renvoie {(x, y, z): minerai}."""
    rng = random.Random(20261009)
    cells = {(x, y, z) for (x1, z1, x2, z2) in ORE_RECTS for x in range(x1, x2 + 1)
             for z in range(z1, z2 + 1) for y in range(PIT_Y[0], PIT_Y[1] + 1)}
    ordered = sorted(cells)
    ores = {}
    # (minerai, nombre de filons, taille min, taille max, Y max) : du plus courant au plus rare
    table = [("coal_ore", 46, 5, 10, 68), ("copper_ore", 30, 4, 8, 68), ("iron_ore", 36, 3, 7, 68),
             ("redstone_ore", 14, 3, 6, 64), ("lapis_ore", 10, 2, 5, 65), ("gold_ore", 12, 2, 5, 64),
             ("diamond_ore", 5, 1, 3, 61), ("emerald_ore", 3, 1, 1, 66)]
    for ore, n, smin, smax, ymax in table:
        pool = [c for c in ordered if c[1] <= ymax]
        for _ in range(n):
            x, y, z = rng.choice(pool)
            for _ in range(rng.randint(smin, smax)):
                if (x, y, z) in cells:
                    ores[(x, y, z)] = ore
                dx, dy, dz = rng.choice([(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)])
                x, y, z = x + dx, y + dy, z + dz
    return ores, len(cells)


def box_fill_cmds():
    """Commandes de box_fill : la box (2 × 2 chunks) est entièrement remise à neuf."""
    x1, z1, x2, z2 = BOX
    b = Build(BOX)
    y0, y1 = PIT_Y
    b.raw("# Joueurs dans la fosse (ou qui ont creusé autour) : renvoyés à la surface, au nord de la box",
          f"tp @a[x={x1},y=40,z={z1},dx={x2 - x1},dy={G - 41},dz={z2 - z1}] {(x1 + x2 + 1) / 2} {G} {z1 - 2.5}")
    b.raw("# Terrain des 4 chunks : pierre, terre, herbe, air au-dessus")
    b.fill(x1, 50, z1, x2, 57, z2, "minecraft:stone")
    b.fill(x1, 58, z1, x2, FLOOR - 1, z2, "minecraft:dirt")
    b.fill(x1, FLOOR, z1, x2, FLOOR, z2, "minecraft:grass_block")
    b.fill(x1, G, z1, x2, WALL_TOP, z2, "minecraft:air")
    b.raw("# Bord en deepslate polie, fond et parois en deepslate renforcée (incassable)")
    b.fill(WX1 - 1, FLOOR, WZ1 - 1, WX2 + 1, FLOOR, WZ2 + 1, "minecraft:polished_deepslate")
    b.fill(WX1, y0 - 1, WZ1, WX2, y1, WZ2, "minecraft:reinforced_deepslate")
    b.raw("# Remplissage : pierre puis filons (graine fixe)")
    for (rx1, rz1, rx2, rz2) in ORE_RECTS:
        b.fill(rx1, y0, rz1, rx2, y1, rz2, "minecraft:stone")
    ores, _ = ore_layout()
    for (x, y, z), ore in sorted(ores.items()):
        b.set(x, y, z, f"minecraft:{ore}")
    b.raw("# Escalier d'accès (non rempli) : 9 marches le long du mur ouest, puis un palier")
    sx1, sx2 = STAIR_X
    for i in range(STAIR_N):
        z, y = STAIR_Z0 + i, y1 - 1 - i
        if y - 1 >= y0:
            b.fill(sx1, y0, z, sx2, y - 1, z, "minecraft:deepslate_bricks")
        b.fill(sx1, y, z, sx2, y, z, "minecraft:deepslate_brick_stairs[facing=north]")
        b.fill(sx1, y + 1, z, sx2, y1, z, "minecraft:air")
    b.fill(sx1, y0, LANDING[0], sx2, y1, LANDING[1], "minecraft:air")
    b.raw("# Garde-corps sur les parois, ouverture en haut de l'escalier, piliers à lanterne")
    for r in ((WX1, WZ1, WX2, WZ1), (WX1, WZ2, WX2, WZ2), (WX1, WZ1, WX1, WZ2), (WX2, WZ1, WX2, WZ2)):
        b.fill(r[0], G, r[1], r[2], G, r[3], "minecraft:deepslate_brick_wall")
    b.fill(sx1, G, WZ1, sx2, G, WZ1, "minecraft:air")
    posts = [(x, z) for x in range(WX1, WX2 + 1, 5) for z in (WZ1, WZ2)] + \
            [(x, z) for z in range(WZ1 + 5, WZ2, 5) for x in (WX1, WX2)] + [(WX2, WZ1), (WX2, WZ2), (WX1, WZ2)]
    for x, z in sorted(set(posts)):
        if not (sx1 - 1 <= x <= sx2 + 1 and z == WZ1):
            b.set(x, G, z, "minecraft:deepslate_bricks")
            b.set(x, G + 1, z, "minecraft:lantern")
            lights.append((x, G + 1, z))
    b.set(sx1 - 1, G, WZ1, "minecraft:deepslate_bricks"); b.set(sx1 - 1, G + 1, WZ1, "minecraft:lantern")
    b.set(sx2 + 1, G, WZ1, "minecraft:deepslate_bricks"); b.set(sx2 + 1, G + 1, WZ1, "minecraft:lantern")
    lights.extend([(sx1 - 1, G + 1, WZ1), (sx2 + 1, G + 1, WZ1)])
    b.raw("# Lumière invisible au-dessus de la fosse (évite les monstres sur les minerais)")
    for x in (PIT[0] + 4, PIT[0] + 12, PIT[0] + 20):
        for z in (PIT[1] + 4, PIT[1] + 12, PIT[1] + 20):
            b.set(x, G + 3, z, "minecraft:light[level=15]")
            lights.append((x, G + 3, z))
    b.raw("# Lanternes autour de la box (dans les 4 chunks)")
    for x, z in ((x1 + 1, z1 + 1), (x2 - 1, z1 + 1), (x1 + 1, z2 - 1), (x2 - 1, z2 - 1),
                 ((x1 + x2) // 2, z1 + 1), ((x1 + x2) // 2, z2 - 1), (x1 + 1, (z1 + z2) // 2), (x2 - 1, (z1 + z2) // 2)):
        b.fill(x, G, z, x, G + 2, z, "minecraft:dark_oak_fence")
        b.set(x, G + 3, z, "minecraft:lantern")
        lights.append((x, G + 3, z))
    return b.cmds, b.vol


def build_box(job, vol):
    mark(BOX, ZONE)
    mark((BOX[0] + 12, BOX[1] - 4, BOX[0] + 19, BOX[1] - 1), ZONE)     # point d'arrivée du tp : pas de lampadaire
    job.add(BOX, ["# Box : structure complète et premier remplissage", f"function {NS}:spawn/box_fill"],
            vol, "box", alone=True)


# --------------------------------------------------------------------------------------------
# 8. Arène de duel (nord) : au niveau du plateau, sur 2 × 2 chunks exacts
# --------------------------------------------------------------------------------------------
def build_arena(job):
    x1, z1, x2, z2 = ARENA
    area = (x1 - 6, z1 - 6, x2 + 6, z2 + 6)
    b = Build(area)
    ex1, ex2 = ALLEE_NS                         # entrée au sud, dans l'axe de l'allée nord
    b.raw("# Ancienne KOTH (balise 77,64,-2180, verre 77,65,-2180, fer 76..78,63,-2181..-2179) : "
          "recouverte par le sol de l'arène, remontée au niveau du plateau")
    b.fill(x1, 60, z1, x2, FLOOR - 1, z2, "minecraft:stone_bricks")
    b.fill(x1, FLOOR, z1, x2, FLOOR, z2, "minecraft:stone_bricks")
    b.fill(x1 + 2, FLOOR, z1 + 2, x2 - 2, FLOOR, z2 - 2, "minecraft:polished_andesite")
    b.fill(x1 + 14, FLOOR, z1 + 14, x2 - 14, FLOOR, z2 - 14, "minecraft:chiseled_stone_bricks")
    b.fill(x1 + 14, FLOOR, z1 + 4, x2 - 14, FLOOR, z1 + 5, "minecraft:red_concrete")       # départ nord
    b.fill(x1 + 14, FLOOR, z2 - 5, x2 - 14, FLOOR, z2 - 4, "minecraft:blue_concrete")      # départ sud
    b.fill(x1, G, z1, x2, G + 16, z2, "minecraft:air")
    for x in (x1 + 3, x1 + 11, x2 - 11, x2 - 3):
        for z in (z1 + 3, z1 + 11, z2 - 11, z2 - 3):
            light(b, x, FLOOR, z, "minecraft:sea_lantern")
    # muret (1) + vitres (2) : 3 de haut, piliers à lanterne, entrée de 6 au sud
    for r in ((x1, z1, x2, z1), (x1, z2, x2, z2), (x1, z1, x1, z2), (x2, z1, x2, z2)):
        b.fill(r[0], G, r[1], r[2], G, r[3], "minecraft:stone_bricks")
        b.fill(r[0], G + 1, r[1], r[2], G + 2, r[3], "minecraft:glass_pane")
        mark(r, OBST)
    b.fill(ex1, G, z2, ex2, G + 2, z2, "minecraft:air")
    b.fill(ex1, FLOOR, z2, ex2, FLOOR, z2, "minecraft:polished_andesite")
    mark((ex1, z2, ex2, z2), ZONE)
    pillars = sorted({(x, z) for x in list(range(x1, x2 + 1, 8)) + [x2] for z in (z1, z2)} |
                     {(x, z) for z in list(range(z1, z2 + 1, 8)) + [z2] for x in (x1, x2)})
    for x, z in pillars:
        if z == z2 and ex1 - 1 <= x <= ex2 + 1:
            continue
        b.fill(x, G, z, x, G + 3, z, "minecraft:stone_bricks")
        b.set(x, G + 4, z, "minecraft:lantern")
    for x in (ex1 - 1, ex2 + 1):
        b.fill(x, G, z2, x, G + 3, z2, "minecraft:stone_bricks")
        b.set(x, G + 4, z2, "minecraft:lantern")
    # gradins à l'extérieur (en safezone) : 3 rangs de sièges et un mur de fond à lanternes
    stands = [  # (rectangle du 1er rang, direction de recul, orientation des marches)
        ((x1 - 1, z1 + 1, x1 - 1, z2 - 1), (-1, 0), "west"),
        ((x2 + 1, z1 + 1, x2 + 1, z2 - 1), (1, 0), "east"),
        ((x1 + 2, z1 - 1, x2 - 2, z1 - 1), (0, -1), "north"),
        ((x1 + 2, z2 + 1, ex1 - 3, z2 + 1), (0, 1), "south"),
        ((ex2 + 3, z2 + 1, x2 - 2, z2 + 1), (0, 1), "south"),
    ]
    for (sx1, sz1, sx2, sz2), (dx, dz), f in stands:
        for t in range(4):
            rx1, rz1, rx2, rz2 = sx1 + dx * t, sz1 + dz * t, sx2 + dx * t, sz2 + dz * t
            if t < 3:
                if t:
                    b.fill(rx1, G, rz1, rx2, G + t - 1, rz2, "minecraft:stone_bricks")
                b.fill(rx1, G + t, rz1, rx2, G + t, rz2, f"minecraft:spruce_stairs[facing={f}]")
            else:
                b.fill(rx1, G, rz1, rx2, G + 3, rz2, "minecraft:stone_bricks")
                ln = max(rx2 - rx1, rz2 - rz1)
                for u in range(0, ln + 1, 5):
                    lx, lz = (rx1 + u, rz1) if rx2 > rx1 else (rx1, rz1 + u)
                    b.set(lx, G + 4, lz, "minecraft:lantern")
            mark((min(rx1, rx2), min(rz1, rz2), max(rx1, rx2), max(rz1, rz2)), OBST)
    mark(area, ZONE)
    job.add(area, b.cmds, b.vol, "arène de duel")


# --------------------------------------------------------------------------------------------
# 9. Éclairage : calcul de lumière au sol et lampadaires automatiques sur le plateau
# --------------------------------------------------------------------------------------------
def med_lights():
    """Lanternes du muret du spawn médiéval (Y=71), qui éclairent aussi l'extérieur."""
    out = []
    for x in (8, 16, 24, 40, 48, 56):
        for lx, lz in ((x, 0), (63, x), (63 - x, 63), (0, 63 - x)):
            out.append((MED[0] + lx, 71, MED[1] + lz))
    return out


def light_map(sources):
    """Propagation 2D de la lumière (−1 par bloc, arrêtée par les obstacles)."""
    lv = [[0] * PD for _ in range(PW)]
    buckets = [[] for _ in range(16)]
    for x, y, z in sources:
        if PX1 <= x <= PX2 and PZ1 <= z <= PZ2:
            l = 15 - abs(y - G)
            if l > lv[x - PX1][z - PZ1]:
                lv[x - PX1][z - PZ1] = l
                buckets[l].append((x - PX1, z - PZ1))
    for l in range(15, 1, -1):
        for i, k in buckets[l]:
            if lv[i][k] != l:
                continue
            for a, c in ((i + 1, k), (i - 1, k), (i, k + 1), (i, k - 1)):
                if 0 <= a < PW and 0 <= c < PD and plan[a][c] < OBST and lv[a][c] < l - 1:
                    lv[a][c] = l - 1
                    buckets[l - 1].append((a, c))
    return lv


def build_lighting(job):
    # bords : cases libres voisines d'un quartier ou d'un obstacle (pas de lampadaire, mais éclairées)
    edge = []
    for i in range(PW):
        for k in range(PD):
            if plan[i][k] == FREE:
                for a in range(i - 1, i + 2):
                    for c in range(k - 1, k + 2):
                        if 0 <= a < PW and 0 <= c < PD and plan[a][c] != FREE and plan[a][c] != EDGE:
                            edge.append((i, k))
                            break
                    else:
                        continue
                    break
    for i, k in edge:
        plan[i][k] = EDGE
    # réseau de lampadaires en quinconce (pas de 18) sur les cases libres
    posts = []
    S = 18
    for j, z in enumerate(range(PZ1 + 4, PZ2 + 1, S // 2)):
        for x in range(PX1 + 4 + (S // 2 if j % 2 else 0), PX2 + 1, S):
            if plan[x - PX1][z - PZ1] == FREE:
                posts.append((x, z))
    sources = lights + med_lights() + [(x, G + 3, z) for x, z in posts]
    # complément : tant qu'il reste une case sombre, un lampadaire sur la case libre la plus proche
    added = 0
    post_set = {(x - PX1, z - PZ1) for x, z in posts}
    tried = set()
    while True:
        lv = light_map(sources)
        dark = [(i, k) for i in range(PW) for k in range(PD) if lv[i][k] == 0 and plan[i][k] < OBST]
        if not dark:
            break
        i, k = dark[0]
        if (i, k) in tried:
            raise SystemExit(f"case sombre impossible à éclairer : {(i + PX1, k + PZ1)} (classe {plan[i][k]})")
        tried.add((i, k))
        spot = None
        for r in range(0, 12):
            cand = [(a, c) for a in range(i - r, i + r + 1) for c in range(k - r, k + r + 1)
                    if 0 <= a < PW and 0 <= c < PD and plan[a][c] == FREE
                    and (a, c) not in post_set]
            if cand:
                spot = min(cand, key=lambda p: (abs(p[0] - i) + abs(p[1] - k), p))
                break
        if spot is None:
            raise SystemExit(f"case sombre impossible à éclairer : {(i + PX1, k + PZ1)} (classe {plan[i][k]})")
        posts.append((spot[0] + PX1, spot[1] + PZ1))
        post_set.add(spot)
        sources.append((spot[0] + PX1, G + 3, spot[1] + PZ1))
        added += 1
    lv = light_map(sources)
    minimum = min(lv[i][k] for i in range(PW) for k in range(PD) if plan[i][k] < OBST)
    # commandes, groupées par carrés de 64 blocs pour des étapes compactes
    posts.sort(key=lambda p: ((p[1] - PZ1) // 64, (p[0] - PX1) // 64, p[1], p[0]))
    groups = {}
    for x, z in posts:
        groups.setdefault(((z - PZ1) // 64, (x - PX1) // 64), []).append((x, z))
    for key in sorted(groups):
        pts = groups[key]
        area = (min(p[0] for p in pts), min(p[1] for p in pts), max(p[0] for p in pts), max(p[1] for p in pts))
        b = Build(area)
        for x, z in pts:
            b.fill(x, G, z, x, G + 2, z, "minecraft:dark_oak_fence")
            b.set(x, G + 3, z, "minecraft:lantern")
        job.add(area, b.cmds, b.vol, "lampadaires")
    return len(posts), added, minimum


# --------------------------------------------------------------------------------------------
# Vérifications : volumes des fill, emprise du spawn, spawn médiéval intact
# --------------------------------------------------------------------------------------------
NUM = r"(-?\d+(?:\.\d+)?)"


def check_line(line, where):
    s = line.strip()
    if not s or s.startswith("#"):
        return
    boxes = []
    if s.startswith("fill "):
        v = [int(t) for t in s.split()[1:7]]
        b = (min(v[0], v[3]), min(v[1], v[4]), min(v[2], v[5]), max(v[0], v[3]), max(v[1], v[4]), max(v[2], v[5]))
        assert vol_of(b) <= FILL_LIMIT, f"{where} : fill de {vol_of(b)} blocs > {FILL_LIMIT} : {s}"
        boxes.append(b)
    elif s.startswith("setblock "):
        x, y, z = (int(t) for t in s.split()[1:4])
        boxes.append((x, y, z, x, y, z))
    elif s.startswith("summon ") or s.startswith("tp "):
        m = re.match(rf"(?:summon \S+|tp \S+) {NUM} {NUM} {NUM}", s)
        assert m, f"{where} : position illisible : {s}"
        x, y, z = (float(t) for t in m.groups())
        boxes.append((int(x // 1), int(y // 1), int(z // 1)) * 2)
    for (x1, y1, z1, x2, y2, z2) in boxes:
        assert SPAWN[0] <= x1 and x2 <= SPAWN[2] and SPAWN[1] <= z1 and z2 <= SPAWN[3], \
            f"{where} : hors de l'emprise du spawn : {s}"
        assert x2 < MED[0] or x1 > MED[2] or z2 < MED[1] or z1 > MED[3], \
            f"{where} : écrit dans le spawn médiéval : {s}"


# --------------------------------------------------------------------------------------------
# Écriture du datapack
# --------------------------------------------------------------------------------------------
def rects_of(chunks):
    """Ensemble de chunks → rectangles (en blocs) pour forceload, par bandes fusionnées."""
    rows = {}
    for cx, cz in chunks:
        rows.setdefault(cz, []).append(cx)
    runs = []
    for cz in sorted(rows):
        xs = sorted(rows[cz])
        start = prev = xs[0]
        for cx in xs[1:] + [None]:
            if cx is not None and cx == prev + 1:
                prev = cx
                continue
            runs.append((start, prev, cz))
            if cx is not None:
                start = prev = cx
    rects = []
    for a, b, cz in runs:
        for r in rects:
            if r[0] == a and r[1] == b and r[3] == cz - 1:
                r[3] = cz
                break
        else:
            rects.append([a, b, cz, cz])
    return [(a * 16, c * 16, b * 16 + 15, d * 16 + 15) for a, b, c, d in rects]


def fl(chunks):
    out = []
    for x1, z1, x2, z2 in rects_of(chunks):
        assert len(chunks_of((x1, z1, x2, z2), 0)) <= 256
        out.append(f"{x1} {z1} {x2} {z2}")
    return out


def write(job, box_cmds):
    if OUT.exists():
        shutil.rmtree(OUT)
    fdir = OUT / "data" / NS / "function" / "spawn"
    fdir.mkdir(parents=True)
    (OUT / "pack.mcmeta").write_text(json.dumps(
        {"pack": {"description": "Spawn LifeCraft : plateau, rempart, porte PvP, quartiers, box, arène",
                  "min_format": 107, "max_format": 107}}, ensure_ascii=False, indent=2) + "\n")

    steps = job.steps
    n = len(steps)
    files = {}
    first = chunks_of(steps[0]["area"])
    files["build"] = [
        "# Construit le spawn complet par étapes (voir README). Ne pas lancer deux fois en même temps.",
        "# Chaque étape forceload la zone de la suivante, puis libère les chunks dont elle n'a plus besoin.",
        f"say Spawn : début de la construction ({n} étapes)",
        *[f"forceload add {r}" for r in fl(first)],
        f"schedule function {NS}:spawn/step_0 40t",
    ]
    for i, st in enumerate(steps):
        cur = chunks_of(st["area"])
        nxt = chunks_of(steps[i + 1]["area"]) if i + 1 < n else set()
        lines = [f"# Étape {i + 1}/{n} : {', '.join(st['labels'])}"]
        lines += [f"forceload add {r}" for r in fl(nxt - cur)] if i + 1 < n else []
        lines += st["cmds"]
        lines += [f"forceload remove {r}" for r in fl(cur - nxt)]
        if (i + 1) % max(1, n // 10) == 0 and i + 1 < n:
            lines.append(f"say Spawn : {100 * (i + 1) // n} %")
        if i + 1 < n:
            lines.append(f"schedule function {NS}:spawn/step_{i + 1} {steps[i + 1]['delay']}")
        else:
            lines += ["# Box : remise à neuf toutes les 10 minutes à partir de maintenant",
                      f"function {NS}:spawn/box_tick",
                      "say Spawn : construction terminée"]
        files[f"step_{i}"] = lines

    bx1, bz1, bx2, bz2 = BOX
    loaded = " ".join(f"if loaded {x} 0 {z}" for x in (bx1, bx2) for z in (bz1, bz2))
    files["box_fill"] = ["# Remplit la box (ne pas appeler directement : passer par box_reset, qui charge les chunks)",
                         *box_cmds]
    files["box_reset"] = [
        "# Remise à neuf de la box (X -64..-33, Z -2032..-2001). Si ses 4 chunks sont déjà chargés,",
        "# remplissage immédiat ; sinon on les force le temps du remplissage (1 s) puis on les libère.",
        "scoreboard objectives add lifecraft_box dummy",
        "scoreboard players set #charge lifecraft_box 0",
        f"execute {loaded} run scoreboard players set #charge lifecraft_box 1",
        f"execute if score #charge lifecraft_box matches 1 run function {NS}:spawn/box_fill",
        f"execute if score #charge lifecraft_box matches 0 run forceload add {bx1} {bz1} {bx2} {bz2}",
        f"execute if score #charge lifecraft_box matches 0 run schedule function {NS}:spawn/box_reset_forced 20t",
    ]
    files["box_reset_forced"] = [
        "# Suite de box_reset quand les chunks ont dû être forcés : remplit s'ils sont chargés, puis les libère",
        "scoreboard players set #charge lifecraft_box 0",
        f"execute {loaded} run scoreboard players set #charge lifecraft_box 1",
        f"execute if score #charge lifecraft_box matches 1 run function {NS}:spawn/box_fill",
        "execute if score #charge lifecraft_box matches 0 run say Box : chunks pas encore chargés, remise à neuf au prochain passage",
        f"forceload remove {bx1} {bz1} {bx2} {bz2}",
    ]
    files["box_tick"] = [
        "# Remet la box à neuf, puis se replanifie dans 10 minutes (schedule remplace l'ancien : pas de doublon)",
        f"function {NS}:spawn/box_reset",
        "# Enclumes de la salle d'enchantement : remplacées par des neuves (elles s'abîment à l'usage et",
        "# personne ne peut les reposer en safezone). Seulement si leur chunk est chargé.",
        *[f"execute if loaded {x} 0 {z} run setblock {x} {G} {z} minecraft:anvil[facing=north]" for x, z in ANVILS],
        f"schedule function {NS}:spawn/box_tick {BOX_PERIOD}",
    ]

    total = 0
    for name, lines in files.items():
        for ln in lines:
            check_line(ln, name)
        total += sum(1 for ln in lines if ln.strip() and not ln.startswith("#"))
        (fdir / f"{name}.mcfunction").write_text("\n".join(lines) + "\n")
    return n, total, files


def main():
    job = Job()
    mark(MED, MEDC)
    build_plateau(job)
    build_rampart(job)
    job.say("Spawn : porte PvP posée, allées et quartiers...")
    build_allees(job)
    build_plaza(job)
    build_hall(job)
    build_market(job)
    job.say("Spawn : marché et PNJ en place, box et arène...")
    box_cmds, box_vol = box_fill_cmds()
    build_box(job, box_vol)
    build_arena(job)
    job.say("Spawn : quartiers terminés, éclairage...")
    nposts, added, minimum = build_lighting(job)
    n, total, files = write(job, box_cmds)
    ores, cells = ore_layout()
    counts = {}
    for o in ores.values():
        counts[o] = counts.get(o, 0) + 1
    delay_s = sum(int(s["delay"][:-1]) for s in job.steps) / 20 + 2
    big = max(len(chunks_of(s["area"])) for s in job.steps)
    print(f"{OUT} : {n} étapes, {total} commandes, durée estimée ≈ {delay_s:.0f} s (hors lag)")
    print(f"  chunks forcés au plus par étape : {big} ; volume max d'une étape : {max(s['vol'] for s in job.steps)}")
    print(f"  lampadaires du plateau : {nposts} (dont {added} ajoutés pour les coins sombres), "
          f"lumière minimale au sol : {minimum}")
    print(f"  box : {cells} blocs à remplir, {len(files['box_fill'])} lignes ; minerais : "
          + ", ".join(f"{k} {v}" for k, v in sorted(counts.items(), key=lambda kv: -kv[1])))


if __name__ == "__main__":
    main()
