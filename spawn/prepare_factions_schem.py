#!/usr/bin/env python3
"""Prépare factions.schem (factions.zip) pour le coller avec FAWE : écrit factions_ile.schem.

Deux corrections, sans toucher au reste du build :
  - les 74 629 feuilles de jungle non persistantes (distance=7) pourrissaient dès le collage :
    elles deviennent persistantes ;
  - les colonnes entièrement vides autour de l'île (148 550 sur 678 × 655) deviennent du
    structure_void. Collées avec `//paste -m !structure_void`, elles laissent le terrain en place
    au lieu de creuser jusqu'à Y=-2.

Usage : python3 -I spawn/prepare_factions_schem.py factions.schem factions_ile.schem
Nécessite numpy (apt install python3-numpy).
"""
import gzip
import struct
import sys

import numpy as np

LEAVES_OLD = "minecraft:jungle_leaves[distance=7,persistent=false,waterlogged=false]"
LEAVES_NEW = "minecraft:jungle_leaves[distance=6,persistent=true,waterlogged=false]"
AIR = ("minecraft:air", "minecraft:cave_air", "minecraft:void_air")


def skip(d, p, t):
    """Position juste après une valeur NBT de type t commençant en p."""
    fixed = {1: 1, 2: 2, 3: 4, 4: 8, 5: 4, 6: 8}
    if t in fixed:
        return p + fixed[t]
    if t in (7, 11, 12):
        n = struct.unpack_from(">i", d, p)[0]
        return p + 4 + n * {7: 1, 11: 4, 12: 8}[t]
    if t == 8:
        return p + 2 + struct.unpack_from(">H", d, p)[0]
    if t == 9:
        et, n = d[p], struct.unpack_from(">i", d, p + 1)[0]
        p += 5
        for _ in range(n):
            p = skip(d, p, et)
        return p
    if t == 10:
        while d[p] != 0:
            ln = struct.unpack_from(">H", d, p + 1)[0]
            p = skip(d, p + 3 + ln, d[p])
        return p + 1
    raise ValueError(f"type NBT inconnu {t}")


def entries(d, p):
    """Entrées d'un compound commençant en p : {nom: (début de l'entrée, début valeur, fin, type)}."""
    out = {}
    while d[p] != 0:
        t, ln = d[p], struct.unpack_from(">H", d, p + 1)[0]
        name = d[p + 3:p + 3 + ln].decode()
        vs = p + 3 + ln
        pe = skip(d, vs, t)
        out[name] = (p, vs, pe, t)
        p = pe
    return out


def nbt_str(s):
    b = s.encode()
    return struct.pack(">H", len(b)) + b


def main(src, dst):
    d = gzip.decompress(open(src, "rb").read())
    root = 3 + struct.unpack_from(">H", d, 1)[0]
    top = entries(d, root)
    if "Schematic" in top:
        raise SystemExit("format Sponge v3 non géré (attendu : v2, comme factions.zip)")
    ival = lambda k: struct.unpack_from(">h" if top[k][3] == 2 else ">i", d, top[k][1])[0]
    W, H, L = ival("Width"), ival("Height"), ival("Length")

    _, pvs, ppe, _ = top["Palette"]
    pal = {k: struct.unpack_from(">i", d, vs)[0] for k, (_, vs, _, _) in entries(d, pvs).items()}

    _, bvs, bpe, _ = top["BlockData"]
    raw = np.frombuffer(d, np.uint8, struct.unpack_from(">i", d, bvs)[0], bvs + 4)
    # varints : palettes < 16 384 entrées, donc 1 ou 2 octets
    starts = np.flatnonzero(np.concatenate(([True], raw[:-1] < 0x80)))
    ids = (raw[starts] & 0x7F).astype(np.int32)
    two = raw[starts] >= 0x80
    ids[two] |= raw[starts[two] + 1].astype(np.int32) << 7
    assert ids.size == W * H * L, "BlockData ne correspond pas aux dimensions"
    a = ids.reshape(H, L, W)

    air = [i for k, i in pal.items() if k.split("[")[0] in AIR]
    empty = np.isin(a, air).all(axis=0)
    sv = max(pal.values()) + 1
    a[:, empty] = sv
    f = a.ravel()
    big = f >= 0x80
    enc = np.empty(f.size + int(big.sum()), np.uint8)
    pos = np.arange(f.size) + np.concatenate(([0], np.cumsum(big)[:-1]))
    enc[pos] = (f & 0x7F) | np.where(big, 0x80, 0)
    enc[pos[big] + 1] = f[big] >> 7

    pbytes = d[pvs:ppe - 1]
    if LEAVES_OLD in pal:
        assert LEAVES_NEW not in pal
        pbytes = pbytes.replace(nbt_str(LEAVES_OLD), nbt_str(LEAVES_NEW))
    pbytes += b"\x03" + nbt_str("minecraft:structure_void") + struct.pack(">i", sv) + b"\x00"

    parts = []
    for k, (ks, vs, pe, _) in sorted(top.items(), key=lambda e: e[1][0]):
        if k == "Palette":
            parts.append(d[ks:vs] + pbytes)
        elif k == "PaletteMax":
            parts.append(d[ks:vs] + struct.pack(">i", sv + 1))
        elif k == "BlockData":
            parts.append(d[ks:vs] + struct.pack(">i", enc.size) + enc.tobytes())
        else:
            parts.append(d[ks:pe])
    open(dst, "wb").write(gzip.compress(d[:root] + b"".join(parts) + b"\x00", 6))
    print(f"{dst} : {int(empty.sum())} colonnes vides ignorées, feuilles rendues persistantes")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2])
