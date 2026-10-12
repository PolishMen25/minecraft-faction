#!/usr/bin/env bash
# Spawn de factions.zip : installe le datapack lifecraft_factions (box + marché) et recale les zones
# des plugins sur le nouveau build. À lancer serveur arrêté, puis au démarrage :
#   /function lifecraft:factions/start
#   - LifeKoth : zone libre « box » (clairière nord), « duel » (hall ouest), classement près du spawn
#   - LifeCore : la box est exclue de spawn-guard (les minerais doivent tomber)
#   - Essentials : /spawn au centre de la place (100.5, 72, -2087.5), warps box, duel, marche, enchant
set -euo pipefail
REPO="$(cd "$(dirname "$0")/.." && pwd)"
MC="${MC_DIR:-/opt/minecraft}"
BK="$MC/backups"
STAMP=$(date +%Y%m%d-%H%M)
if systemctl is-active --quiet minecraft; then
  echo "Arrête d'abord le serveur : systemctl stop minecraft" >&2
  exit 1
fi

python3 "$REPO/spawn/generate_factions_spawn.py"
rm -rf "$MC/world/datapacks/lifecraft_factions"
cp -r "$REPO/spawn/datapack/lifecraft_factions" "$MC/world/datapacks/"
chown -R minecraft:minecraft "$MC/world/datapacks/lifecraft_factions"

KOTH="$MC/plugins/LifeKoth/config.yml"
CORE="$MC/plugins/LifeCore/config.yml"
ESS="$MC/plugins/Essentials/spawn.yml"
cp -p "$ESS" "$BK/spawn.yml.bak-$STAMP"
sed -i -E 's/^(    x: ).*/\1100.5/; s/^(    y: ).*/\172.0/; s/^(    z: ).*/\1-2087.5/' "$ESS"
WORLD_UUID=$(sed -n 's/^    world: //p' "$ESS" | head -1)
install -d -o minecraft -g minecraft "$MC/plugins/Essentials/warps"
warp() {  # nom x y z yaw
  printf 'world: %s\nworld-name: world\nx: %s\ny: %s\nz: %s\nyaw: %s\npitch: 0.0\nname: %s\n' \
    "$WORLD_UUID" "$2" "$3" "$4" "$5" "$1" > "$MC/plugins/Essentials/warps/$1.yml"
}
warp box 109.5 52.0 -2254.5 0.0          # bord nord de la box
warp duel 38.5 69.0 -2087.5 90.0         # entrée du hall ouest
warp marche 161.5 69.0 -2087.5 -90.0     # entrée du hall est
warp enchant 71.5 72.0 -2056.5 180.0     # devant la table d'enchantement
chown -R minecraft:minecraft "$MC/plugins/Essentials/warps"
cp -p "$KOTH" "$BK/LifeKoth-config.yml.bak-$STAMP"
cp -p "$CORE" "$BK/LifeCore-config.yml.bak-$STAMP"

python3 -I - "$KOTH" "$CORE" <<'EOF'
import re, sys
koth, core = sys.argv[1], sys.argv[2]

def set_block(text, header, values):
    """Remplace les clés numériques (x1, y1, ...) d'une section indentée qui suit `header`."""
    i = text.index(header)
    j = i + len(header)
    end = re.search(r"\n(?!    )", text[j:])
    orig = text[j:j + end.start()] if end else text[j:]
    blk = orig
    for k, v in values.items():
        blk, n = re.subn(rf"(\n    {k}: )[^\n]*", rf"\g<1>{v}", blk)
        assert n == 1, (header, k)
    return text[:j] + blk + text[j + len(orig):]

t = open(koth).read()
t = set_block(t, "\n  box:", dict(x1=93, y1=23, z1=-2255, x2=124, y2=63, z2=-2224))
t = set_block(t, "\n  duel:", dict(x1=13, y1=68, z1=-2106, x2=35, y2=88, z2=-2068))
t = t.replace("# Box de minage du spawn", "# Box de minage du spawn (clairière au nord de la place)")
t = t.replace("# Arène de duel du spawn", "# Arène de duel du spawn (hall couvert à l'ouest de la place)")
i = t.index("\nleaderboard:")
j = t.index("\n\n", i)
lb = t[i:j]
for k, v in (("x", "100.5"), ("y", "76.0"), ("z", "-2101.5")):
    lb, n = re.subn(rf"(\n  {k}: )[^\n]*", rf"\g<1>{v}", lb)
    assert n == 1, k
t = t[:i] + lb + t[j:]
open(koth, "w").write(t)

c = open(core).read()
if "/warp" not in c:
    c = c.replace('    - "Masque ou affiche le scoreboard avec &e/sb&f."',
                  '    - "Masque ou affiche le scoreboard avec &e/sb&f."\n'
                  '    - "Au spawn : &e/warp marche&f, &e/warp enchant&f, &e/warp box&f et &e/warp duel&f."')
if "\n  exclude:" not in c:
    c, n = re.subn(r"(\n  keep-tag: [^\n]*)", r'\1\n  # Zones où les objets restent permis, « x1,y1,z1,x2,y2,z2 » : la box (les minerais doivent tomber)\n  exclude:\n    - "93,23,-2255,124,70,-2224"', c)
    assert n == 1
open(core, "w").write(c)
print("zones LifeKoth et LifeCore mises à jour")
EOF
