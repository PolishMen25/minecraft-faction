#!/usr/bin/env bash
# Déplace le KOTH « Colline » de (72, 79, -2340) à (72, 65, -1625) : datapack + config LifeKoth.
# À lancer dans le conteneur, en root, SERVEUR ARRÊTÉ (LifeKoth réécrit sa config à l'arrêt).
# Puis, serveur démarré : /function lifecraft:warzone/koth_build
set -euo pipefail
MC_DIR="${MC_DIR:-/opt/minecraft}"
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/datapack/lifecraft_warzone"
CFG="$MC_DIR/plugins/LifeKoth/config.yml"

if systemctl is-active --quiet minecraft; then
  echo "Le serveur tourne : arrête-le d'abord (systemctl stop minecraft)." >&2
  exit 1
fi

rm -rf "$MC_DIR/world/datapacks/lifecraft_warzone"
cp -r "$SRC" "$MC_DIR/world/datapacks/"
chown -R minecraft:minecraft "$MC_DIR/world/datapacks/lifecraft_warzone"

cp "$CFG" "$CFG.bak-$(date +%Y%m%d-%H%M%S)"
python3 -I - "$CFG" <<'PY'
import sys
p = sys.argv[1]
s = open(p).read()
old = "  Colline:\n    world: world\n    x: 72\n    y: 79\n    z: -2340\n"
new = "  Colline:\n    world: world\n    x: 72\n    y: 65\n    z: -1625\n"
assert old in s, "zone Colline introuvable ou déjà déplacée"
open(p, "w").write(s.replace(old, new))
PY
echo "KOTH déplacé. Au prochain démarrage, en jeu ou console : /function lifecraft:warzone/koth_build"
