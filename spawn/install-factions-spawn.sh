#!/usr/bin/env bash
# Installe FastAsyncWorldEdit et le schematic du nouveau spawn (factions.zip).
# À lancer dans le conteneur, en root : ./spawn/install-factions-spawn.sh [/root/factions.zip]
set -euo pipefail
MC_DIR="${MC_DIR:-/opt/minecraft}"
ZIP="${1:-/root/factions.zip}"
FAWE_URL="https://cdn.modrinth.com/data/z4HZZnLr/versions/A1gBu4MM/FastAsyncWorldEdit-Paper-2.16.1.jar"
FAWE_JAR="$MC_DIR/plugins/FastAsyncWorldEdit.jar"
SCHEM_DIR="$MC_DIR/plugins/FastAsyncWorldEdit/schematics"

if [[ ! -f "$FAWE_JAR" ]]; then
  # Empreinte publiée par Modrinth pour ce fichier
  SHA512="$(curl -fsS 'https://api.modrinth.com/v2/version/A1gBu4MM' \
    | python3 -I -c 'import json,sys; print([f for f in json.load(sys.stdin)["files"] if f["primary"]][0]["hashes"]["sha512"])')"
  TMP="$(mktemp)"
  curl -fsSL -o "$TMP" "$FAWE_URL"
  echo "$SHA512  $TMP" | sha512sum -c -
  install -o minecraft -g minecraft -m 0644 "$TMP" "$FAWE_JAR"
  rm -f "$TMP"
fi

command -v python3 >/dev/null && python3 -c 'import numpy' 2>/dev/null || apt-get install -y python3-numpy
install -d -o minecraft -g minecraft "$SCHEM_DIR"
python3 -I -c 'import sys,zipfile; open(sys.argv[2],"wb").write(zipfile.ZipFile(sys.argv[1]).read("factions.schem"))' \
  "$ZIP" "$SCHEM_DIR/factions.schem"
# Feuilles persistantes et colonnes vides en structure_void (sinon le collage creuse jusqu'à Y=-2)
python3 -I "$(dirname "$0")/prepare_factions_schem.py" "$SCHEM_DIR/factions.schem" "$SCHEM_DIR/factions_ile.schem"
chown minecraft:minecraft "$SCHEM_DIR"/factions*.schem

echo "FAWE et factions_ile.schem installés. Redémarre le serveur, puis en console :"
echo "  schedule clear lifecraft:spawn/box_tick      (ancienne box : elle écraserait le build)"
echo "  datapack disable \"file/lifecraft_spawn\""
echo "  //world world"
echo "  //pos1 37,1,-2175"
echo "  //schem load factions_ile"
echo "  //paste -m !structure_void"
echo "Le build couvre X -267..410, Y -2..211, Z -2431..-1777 ; la place est à Y=71 (spawn 100.5 72 -2087.5)."
echo "Ensuite, serveur arrêté : spawn/safezone-factions.sh puis spawn/apply-factions-zones.sh ;"
echo "au démarrage : function lifecraft:factions/start"
