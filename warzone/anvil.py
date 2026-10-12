"""Lecture des cartes de hauteur d'un monde Minecraft (fichiers région Anvil, compression LZ4 ou zlib).

heights(cx, cz, key) renvoie un tableau 16 × 16 (index [z][x] dans le chunk) : Y du bloc le plus haut
pour la carte `key` (MOTION_BLOCKING_NO_LEAVES par défaut : sol, troncs et eau, sans feuillage).
Le serveur doit avoir sauvegardé (save-all flush) pour que les fichiers soient à jour.
"""
import gzip
import struct
import sys
import zlib
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "spawn"))
from prepare_factions_schem import entries  # noqa: E402

REGION = Path("/opt/minecraft/world/dimensions/minecraft/overworld/region")
MIN_Y = -64


def _lz4_raw(src):
    out = bytearray()
    i = 0
    while i < len(src):
        t = src[i]
        i += 1
        n = t >> 4
        if n == 15:
            while True:
                b = src[i]
                i += 1
                n += b
                if b != 255:
                    break
        out += src[i:i + n]
        i += n
        if i >= len(src):
            break
        off = src[i] | (src[i + 1] << 8)
        i += 2
        m = t & 15
        if m == 15:
            while True:
                b = src[i]
                i += 1
                m += b
                if b != 255:
                    break
        start = len(out) - off
        for k in range(m + 4):
            out.append(out[start + k])
    return bytes(out)


def _lz4_block_stream(d):
    """Format LZ4Block de lz4-java (compression « lz4 » des régions)."""
    out = bytearray()
    p = 0
    while p + 21 <= len(d) and d[p:p + 8] == b"LZ4Block":
        token = d[p + 8]
        clen, dlen = struct.unpack_from("<ii", d, p + 9)
        p += 21
        if dlen == 0:
            break
        block = d[p:p + clen]
        p += clen
        out += block if token & 0xF0 == 0x10 else _lz4_raw(block)
    return bytes(out)


_regions = {}


def chunk_nbt(cx, cz):
    key = (cx >> 5, cz >> 5)
    if key not in _regions:
        f = REGION / f"r.{key[0]}.{key[1]}.mca"
        _regions[key] = f.read_bytes() if f.exists() else None
    d = _regions[key]
    if d is None:
        return None
    i = 4 * ((cx & 31) + (cz & 31) * 32)
    off = int.from_bytes(d[i:i + 3], "big")
    if off == 0:
        return None
    o = off * 4096
    length, kind = struct.unpack_from(">iB", d, o)
    raw = d[o + 5:o + 4 + length]
    return {1: gzip.decompress, 2: zlib.decompress, 3: bytes, 4: _lz4_block_stream}[kind](raw)


def heights(cx, cz, key="MOTION_BLOCKING_NO_LEAVES"):
    d = chunk_nbt(cx, cz)
    if d is None:
        return None
    top = entries(d, 3 + struct.unpack_from(">H", d, 1)[0])
    if "Heightmaps" not in top:
        return None
    hm = entries(d, top["Heightmaps"][1])
    if key not in hm:
        return None
    vs = hm[key][1]
    n = struct.unpack_from(">i", d, vs)[0]
    vals = []
    for v in struct.unpack_from(f">{n}q", d, vs + 4):
        v &= (1 << 64) - 1
        vals.extend((v >> (9 * k)) & 511 for k in range(7))
    return np.array(vals[:256]).reshape(16, 16) + MIN_Y - 1


class HeightMap:
    """Hauteurs d'une zone rectangulaire (bornes incluses), chargées chunk par chunk."""

    def __init__(self, x1, z1, x2, z2, key="MOTION_BLOCKING_NO_LEAVES"):
        self.x1, self.z1 = x1 & ~15, z1 & ~15
        w, h = (x2 | 15) - self.x1 + 1, (z2 | 15) - self.z1 + 1
        self.h = np.full((h, w), MIN_Y, dtype=int)
        for cx in range(self.x1 >> 4, (x2 >> 4) + 1):
            for cz in range(self.z1 >> 4, (z2 >> 4) + 1):
                c = heights(cx, cz, key)
                if c is None:
                    raise SystemExit(f"chunk {cx},{cz} absent : génère-le (ou save-all flush) avant")
                self.h[cz * 16 - self.z1:cz * 16 - self.z1 + 16, cx * 16 - self.x1:cx * 16 - self.x1 + 16] = c

    def __call__(self, x, z):
        return int(self.h[z - self.z1, x - self.x1])
