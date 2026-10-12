#!/usr/bin/env python3
"""Génère la fonction du chemin entre la porte PvP du spawn et le pont sud de la warzone.

Chaussée de 7 de large (X 69..75, dans l'axe de la porte et du pont), bordures en pierre taillée,
remblai sous la chaussée (remplace l'air et l'eau), dégagement au-dessus, lampadaires tous les 16 blocs.
Elle descend de 2 blocs : sol de la porte en Y 68, tablier du pont en Y 66 (deux paliers en dalles).

Usage : python3 warzone/generate_chemin.py   (écrit warzone/datapack/lifecraft_chemin)
Puis sur le serveur : copier le dossier dans world/datapacks/, /reload,
/function lifecraft:chemin/build (puis attendre « Chemin : construction terminée »).
"""
import json
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.dont_write_bytecode = True
sys.path.insert(0, str(HERE))
import generate_warzone as wz  # noqa: E402

OUT = HERE / "datapack" / "lifecraft_chemin"
NS = wz.NS

GATE_FLOOR = 68                          # sol de la porte PvP (spawn/generate_spawn_districts.py : FLOOR)
_sud = next(b for b in wz.BRIDGES if b[0] == "sud")
CENTER, DECK = _sud[2], _sud[3]          # X 72, tablier en Y 66
A1, A2 = CENTER - 3, CENTER + 3          # chaussée X 69..75
Z_START = wz.SPAWN[3] + 1                # juste devant la porte (Z -1952)
Z_END = wz.INNER[3] + 1 - 14 - 2 - 1     # juste avant l'approche du pont (Z -1801)
FILL_DEPTH = 14                          # remblai sous la chaussée, comme les ponts
CLEAR = 10                               # dégagement au-dessus

# Profil : (z1, z2, niveau du sol). Chaque palier commence par une rangée de dalles.
STEP_1 = Z_END - 29                      # Y 67 à partir de Z -1830
STEP_2 = Z_END - 14                      # Y 66 à partir de Z -1815
PROFILE = [(Z_START, STEP_1 - 1, GATE_FLOOR), (STEP_1, STEP_2 - 1, GATE_FLOOR - 1), (STEP_2, Z_END, DECK)]
assert PROFILE[-1][2] == DECK and PROFILE[0][2] == GATE_FLOOR


def commands():
    cmds = []
    fill = lambda x1, y1, z1, x2, y2, z2, block, mode="": cmds.extend(
        f"fill {b[0]} {b[1]} {b[2]} {b[3]} {b[4]} {b[5]} {block}{' ' + mode if mode else ''}"
        for b in wz.split_box(x1, y1, z1, x2, y2, z2))
    for z1, z2, y in PROFILE:
        # remblai (bordures comprises), puis dégagement au-dessus
        fill(A1 - 1, y - FILL_DEPTH, z1, A2 + 1, y - 1, z2, "minecraft:stone_bricks", "replace #minecraft:replaceable")
        fill(A1 - 1, y - FILL_DEPTH, z1, A2 + 1, y - 1, z2, "minecraft:stone_bricks", "replace minecraft:water")
        fill(A1 - 2, y + 1, z1, A2 + 2, y + CLEAR, z2, "minecraft:air")
        # chaussée, ligne centrale et bordures
        fill(A1, y, z1, A2, y, z2, "minecraft:polished_andesite")
        fill(CENTER, y, z1, CENTER, y, z2, "minecraft:stone_bricks")
        fill(A1 - 1, y, z1, A1 - 1, y, z2, "minecraft:chiseled_stone_bricks")
        fill(A2 + 1, y, z1, A2 + 1, y, z2, "minecraft:chiseled_stone_bricks")
    # paliers : une rangée de dalles au niveau du palier précédent
    for (_, _, y_prev), (z1, _, _) in zip(PROFILE, PROFILE[1:]):
        fill(A1, y_prev, z1, A2, y_prev, z1, "minecraft:stone_brick_slab[type=bottom]")
    # lampadaires sur les bordures
    for z1, z2, y in PROFILE:
        for z in range(z1 + 4, z2 + 1, 16):
            for x in (A1 - 1, A2 + 1):
                cmds.append(f"fill {x} {y + 1} {z} {x} {y + 3} {z} minecraft:stone_brick_wall")
                cmds.append(f"setblock {x} {y + 4} {z} minecraft:lantern")
    return cmds


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    fdir = OUT / "data" / NS / "function" / "chemin"
    fdir.mkdir(parents=True)
    (OUT / "pack.mcmeta").write_text(json.dumps(
        {"pack": {"description": "Chemin LifeCraft : porte PvP du spawn → pont sud",
                  "min_format": 107, "max_format": 107}}, ensure_ascii=False, indent=2) + "\n")
    area = "{} {} {} {}".format(A1 - 3, Z_START - 1, A2 + 3, Z_END + 1)
    (fdir / "build.mcfunction").write_text(
        "# Construit le chemin porte PvP → pont sud (quelques secondes).\n"
        "say Chemin : début de la construction\n"
        f"forceload add {area}\n"
        f"schedule function {NS}:chemin/step 40t\n")
    cmds = commands()
    (fdir / "step.mcfunction").write_text("\n".join(
        cmds + [f"forceload remove {area}", "say Chemin : construction terminée"]) + "\n")
    print(f"{OUT} : {len(cmds)} commandes, Z {Z_START}..{Z_END}, paliers en Z {STEP_1} et {STEP_2}")


if __name__ == "__main__":
    main()
