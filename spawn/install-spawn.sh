#!/usr/bin/env bash
# Copie la structure du spawn dans le monde pour pouvoir la poser avec /place template.
# À lancer dans le conteneur, en root : ./spawn/install-spawn.sh
set -euo pipefail
MC_DIR="${MC_DIR:-/opt/minecraft}"
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/spawn_medieval.nbt"
# Le jeu cherche les structures générées dans generated/<namespace>/structures/ ;
# on copie aussi dans structure/ (nom utilisé par les datapacks depuis la 1.21).
for sub in structures structure; do
  install -D -o minecraft -g minecraft -m 0644 "$SRC" "$MC_DIR/world/generated/lifecraft/$sub/spawn_medieval.nbt"
done
echo "Structure installée. En jeu, debout au centre de l'emplacement voulu :"
echo "  /place template lifecraft:spawn_medieval ~-32 ~-11 ~-32"
