#!/usr/bin/env bash
# Safezone du spawn de factions.zip : chunks X -15..23, Z -151..-113 (blocs X -240..383, Z -2416..-1793),
# soit l'île entière à l'intérieur de la faille. Le reste de la warzone ne change pas.
# À lancer serveur arrêté (FactionsUUID réécrit board.json à l'arrêt).
set -euo pipefail
MC="${MC_DIR:-/opt/minecraft}"
BOARD="$MC/plugins/FactionsUUID/data/board.json"
if systemctl is-active --quiet minecraft; then
  echo "Arrête d'abord le serveur : systemctl stop minecraft" >&2
  exit 1
fi
cp -p "$BOARD" "$MC/backups/board.json.bak-$(date +%Y%m%d-%H%M)"
python3 -I - "$BOARD" <<'EOF'
import json, sys
p = sys.argv[1]
b = json.load(open(p))
w = b.setdefault("world", {})
# ancienne safezone (spawn 304 × 304) rendue à la warzone, puis nouvelle safezone
for k, v in list(w.items()):
    if v == "-1":
        w[k] = "-2"
n = 0
for x in range(-15, 24):
    for z in range(-151, -112):
        w[f"{x},{z}"] = "-1"
        n += 1
json.dump(b, open(p, "w"), indent=2)
print(f"safezone : {n} chunks")
EOF
