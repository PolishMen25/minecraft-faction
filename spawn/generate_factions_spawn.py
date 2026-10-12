#!/usr/bin/env python3
"""Génère le datapack lifecraft_factions : box et marché du spawn de factions.zip.

Le build de factions.zip (collé avec FAWE, voir install-factions-spawn.sh) remplace l'ancien spawn.
Ce datapack y réinstalle :
  - la box de minage, dans la clairière au nord de la place (sol à Y=51) : mêmes fosse, filons et
    escalier que l'ancienne box, décalés depuis le datapack lifecraft_spawn ;
  - le marché PNJ (8 marchands, mêmes échanges), dans le hall couvert à l'est de la place (sol Y=68).
L'arène de duel (hall ouest) et la box sont des zones libres LifeKoth : voir apply-factions-zones.sh.

Usage : python3 spawn/generate_factions_spawn.py   (écrit spawn/datapack/lifecraft_factions)
Puis sur le serveur : copier le dossier dans world/datapacks/, /reload,
/function lifecraft:factions/start  (box remplie puis remise à neuf toutes les 10 min, PNJ posés).
"""
import json
import re
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
OLD = HERE / "datapack" / "lifecraft_spawn" / "data" / "lifecraft" / "function" / "spawn"
OUT = HERE / "datapack" / "lifecraft_factions"
FN = OUT / "data" / "lifecraft" / "function" / "factions"

sys.dont_write_bytecode = True
sys.path.insert(0, str(HERE))
import generate_spawn_districts as sd   # noqa: E402  (échanges des marchands ; main() est protégé)

# Box : l'ancienne (X -64..-33, Z -2032..-2001, sol Y=68) déplacée dans la clairière nord
OLD_BOX = (-64, -2032, -33, -2001)
SHIFT = (157, -17, -223)                 # (dx, dy, dz)
BOX = (OLD_BOX[0] + SHIFT[0], OLD_BOX[1] + SHIFT[2], OLD_BOX[2] + SHIFT[0], OLD_BOX[3] + SHIFT[2])
BOX_PERIOD = "12000t"                    # remise à neuf : toutes les 10 minutes

# Marché : hall est de la place (couvert, sol Y=68), deux rangées de 4 marchands face à face
MARKET_Y = 69
MARKET_ROWS = [(166.5, -90), (181.5, 90)]          # (x, rotation) : ouest regarde l'est et inversement
MARKET_Z = [-2093.5, -2089.5, -2085.5, -2081.5]


def shift_line(line):
    """Décale les coordonnées d'une commande de l'ancienne box (fill, setblock, tp, forceload, loaded)."""
    dx, dy, dz = SHIFT
    if line.startswith("#") or not line.strip():
        return line
    line = line.replace("lifecraft:spawn/", "lifecraft:factions/")

    def sel(m):
        return f"x={int(m[1]) + dx},y={int(m[2]) + dy},z={int(m[3]) + dz}"
    line = re.sub(r"x=(-?\d+),y=(-?\d+),z=(-?\d+)", sel, line)

    def xyz(m):
        x, y, z = (float(v) for v in m.group(2, 3, 4))
        f = lambda v: str(int(v)) if v.is_integer() else str(v)
        return f"{m[1]}{f(x + dx)} {f(y + dy)} {f(z + dz)}"
    # triplets x y z après fill / setblock / tp ... / if loaded (Y=0 de « if loaded » : sans importance)
    if line.startswith(("fill ", "setblock ")):
        return re.sub(r"(^|\s)(-?[\d.]+) (-?[\d.]+) (-?[\d.]+)(?=\s)", xyz, line)
    if line.startswith("tp "):
        return re.sub(r"(\] )(-?[\d.]+) (-?[\d.]+) (-?[\d.]+)$", xyz, line)
    line = re.sub(r"(if loaded )(-?\d+) (-?\d+) (-?\d+)",
                  lambda m: f"{m[1]}{int(m[2]) + dx} {m[3]} {int(m[4]) + dz}", line)
    line = re.sub(r"(forceload (?:add|remove) )(-?\d+) (-?\d+) (-?\d+) (-?\d+)",
                  lambda m: f"{m[1]}{int(m[2]) + dx} {int(m[3]) + dz} {int(m[4]) + dx} {int(m[5]) + dz}", line)
    return line


def box_files():
    files = {}
    for name in ("box_fill", "box_reset", "box_reset_forced"):
        text = (OLD / f"{name}.mcfunction").read_text()
        text = text.replace("(X -64..-33, Z -2032..-2001)", f"(X {BOX[0]}..{BOX[2]}, Z {BOX[1]}..{BOX[3]})")
        files[name] = "\n".join(shift_line(l) for l in text.splitlines()) + "\n"
    files["box_tick"] = (
        "# Remet la box à neuf, puis se replanifie dans 10 minutes (schedule remplace l'ancien : pas de doublon)\n"
        "function lifecraft:factions/box_reset\n"
        f"schedule function lifecraft:factions/box_tick {BOX_PERIOD}\n")
    return files


def market():
    lines = ["# Marché du hall est : on retire d'abord les PNJ d'une pose précédente (pas de doublons)",
             "minecraft:kill @e[type=minecraft:villager,tag=lifecraft_pnj]"]
    sd.G = MARKET_Y
    k = 0
    for x, yaw in MARKET_ROWS:
        for z in MARKET_Z:
            name, color, prof, _job, _wool, trades = sd.MERCHANTS[k]
            k += 1
            lines.append(sd.villager(x, z, yaw, name, color, prof, trades))
    lines.append('say Spawn : 8 marchands posés dans le hall est')
    return "\n".join(lines) + "\n"


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    FN.mkdir(parents=True)
    (OUT / "pack.mcmeta").write_text(json.dumps(
        {"pack": {"description": "Spawn LifeCraft (factions.zip) : box et marché",
                  "min_format": 107, "max_format": 107}}, indent=2, ensure_ascii=False) + "\n")
    for name, text in box_files().items():
        (FN / f"{name}.mcfunction").write_text(text)
    (FN / "market.mcfunction").write_text(market())
    (FN / "start.mcfunction").write_text(
        "# Mise en service : point d'apparition, PNJ du marché, box remplie, puis remise à neuf toutes les 10 minutes\n"
        "setworldspawn 100 72 -2088\n"
        "function lifecraft:factions/market\n"
        "function lifecraft:factions/box_tick\n"
        f"say Spawn : box prête (X {BOX[0]}..{BOX[2]}, Z {BOX[1]}..{BOX[3]})\n")
    print(f"box X {BOX[0]}..{BOX[2]} Z {BOX[1]}..{BOX[3]} ; datapack écrit dans {OUT}")


if __name__ == "__main__":
    main()
