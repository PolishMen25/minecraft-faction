#!/usr/bin/env bash
# Déploiement groupé, en un seul redémarrage :
#   1. sauvegarde complète (mc-backup) et copie des fichiers modifiés
#   2. retrait des claims FactionsUUID hors de la warzone prévue (chunks X -33..41, Z -169..-95)
#   3. réglages de power pour les raids (départ 5, régénération 0.2/min, vampirisme 0.5)
#   4. installation des plugins compilés du dépôt (LifeKoth, LifeCore, LifeItems, LifeShop) s'ils existent
#   5. redémarrage et vérification du démarrage
# Idempotent : le relancer ne change rien de plus.
# Usage : bash ~/minecraft-faction/scripts/deploy-all.sh
set -euo pipefail
REPO="$(cd "$(dirname "$0")/.." && pwd)"
MC=/opt/minecraft
BK="$MC/backups"
STAMP=$(date +%Y%m%d-%H%M)
BOARD="$MC/plugins/FactionsUUID/data/board.json"
MAIN="$MC/plugins/FactionsUUID/config/main.conf"
LOG="$MC/logs/latest.log"

echo "== 1. Sauvegarde"
# Version du dépôt (tolère les changements de la base CoreProtect pendant la lecture)
install -m 755 "$REPO/scripts/mc-backup" /usr/local/bin/mc-backup
mc-backup 2>&1 | tail -2

echo "== 2. Arrêt du serveur"
mc-cmd "say Mise à jour du serveur : redémarrage dans 10 s (environ 1 min)." || true
sleep 10
systemctl stop minecraft
cp -p "$BOARD" "$BK/board.json.bak-$STAMP"
cp -p "$MAIN" "$BK/main.conf.bak-$STAMP"

echo "== 3. Claims hors warzone"
python3 -I - "$BOARD" <<'EOF'
import json, sys
p = sys.argv[1]
b = json.load(open(p))
w = b.get("world", {})
drop = [k for k in w
        if not (-33 <= int(k.split(",")[0]) <= 41 and -169 <= int(k.split(",")[1]) <= -95)]
for k in drop:
    del w[k]
json.dump(b, open(p, "w"), indent=2)
print(f"   claims retirés : {len(drop)}, restants : {len(w)}")
EOF

echo "== 4. Réglages de power"
python3 -I - "$MAIN" <<'EOF'
import re, sys
p = sys.argv[1]
t = open(p).read()
i = t.index("        power {")
j = t.index("        system=power")
blk = t[i:j]
for k, v in (("playerStarting", "5"), ("powerPerMinute", "0.2"), ("vampirism", "0.5")):
    blk, n = re.subn(rf"(\n\s*{k}=)[^\n]*", rf"\g<1>{v}", blk)
    assert n == 1, k
    print(f"   {k} = {v}")
open(p, "w").write(t[:i] + blk + t[j:])
EOF

echo "== 5. Plugins"
OWNER=$(stat -c %U:%G "$MC/plugins")
for spec in koth:LifeKoth core:LifeCore items:LifeItems shop:LifeShop; do
    dir=${spec%%:*}; name=${spec##*:}
    jar=$(ls "$REPO/plugins/$dir/target/$name"-*.jar 2>/dev/null | grep -v original | sort -V | tail -1 || true)
    if [ -n "$jar" ]; then
        [ -f "$MC/plugins/$name.jar" ] && cp -p "$MC/plugins/$name.jar" "$BK/$name.jar.bak-$STAMP"
        cp "$jar" "$MC/plugins/$name.jar"
        chown "$OWNER" "$MC/plugins/$name.jar"
        echo "   $name : $(basename "$jar")"
    else
        echo "   $name : pas de jar compilé, ignoré"
    fi
done

echo "== 6. Démarrage"
systemctl start minecraft
sleep 20
timeout 300 bash -c "until grep -q 'Done (' $LOG; do sleep 3; done"
grep -E "FactionsUUID\] Loaded|Life(Koth|Core|Items)\] Enabling|Done \(" "$LOG" | sed 's/.*INFO\]: //'
echo "   erreurs au démarrage (hors GrimAC/version) :"
grep -iE "error|exception" "$LOG" | grep -viE "GrimAC|unsupported|connect timed" | head -5 || true
echo "== Terminé. Sauvegardes : $BK/*-$STAMP"
