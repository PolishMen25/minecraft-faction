#!/usr/bin/env bash
# Crée les grades LuckPerms du serveur : Joueur (default), VIP, Modo, Admin.
# Relançable sans risque (les commandes LuckPerms sont idempotentes).
#
# Usage (dans le conteneur, serveur démarré) :
#   ./scripts/setup-grades.sh                 # crée les grades
#   ./scripts/setup-grades.sh xPolishMenx     # ... et met ce joueur Admin
set -euo pipefail
MC_DIR="${MC_DIR:-/opt/minecraft}"

lp() { mc-cmd "lp $*"; sleep 0.3; }
perms() { local g="$1"; shift; for p in "$@"; do lp group "$g" permission set "$p" true; done; }

runuser -u minecraft -- tmux has-session -t mc 2>/dev/null \
  || { echo "Le serveur ne tourne pas : systemctl start minecraft" >&2; exit 1; }

echo "Création des groupes..."
for g in vip modo admin; do lp creategroup "$g"; done

# Hiérarchie : default < vip < modo < admin (chaque grade hérite du précédent)
lp group vip parent add default
lp group modo parent add vip
lp group admin parent add modo

# Poids (ordre d'affichage) et préfixes du chat
lp group default setweight 10;  lp group default meta setprefix 10 "\"&7[Joueur] \""
lp group vip setweight 20;      lp group vip meta setprefix 20 "\"&6[VIP] \""
lp group modo setweight 50;     lp group modo meta setprefix 50 "\"&2[Modo] \""
lp group admin setweight 100;   lp group admin meta setprefix 100 "\"&4[Admin] \""

echo "Permissions Joueur..."
perms default \
  essentials.spawn essentials.home essentials.sethome essentials.delhome \
  essentials.tpa essentials.tpaccept essentials.tpdeny \
  essentials.msg essentials.r essentials.ignore essentials.mail essentials.mail.send \
  essentials.list essentials.motd essentials.rules essentials.help essentials.afk \
  essentials.balance essentials.balancetop essentials.pay \
  essentials.warp essentials.warp.list essentials.kit

echo "Permissions VIP..."
perms vip \
  essentials.sethome.multiple essentials.sethome.multiple.vip \
  essentials.workbench essentials.enderchest essentials.hat \
  essentials.nick essentials.chat.color

echo "Permissions Modo..."
perms modo \
  essentials.kick essentials.kick.notify essentials.mute essentials.mute.notify \
  essentials.tempban essentials.ban essentials.ban.notify essentials.unban \
  essentials.jail essentials.togglejail essentials.vanish essentials.invsee \
  essentials.tp essentials.tphere essentials.seen essentials.whois essentials.socialspy \
  essentials.sethome.multiple.staff essentials.chat.url \
  minecraft.command.whitelist

echo "Permissions Admin..."
lp group admin permission set '*' true

if [[ -n "${1:-}" ]]; then
  lp user "$1" parent add admin
  echo "$1 est maintenant Admin."
fi

# Affiche le préfixe du grade dans le chat EssentialsX
ess_cfg="$MC_DIR/plugins/Essentials/config.yml"
if [[ -f "$ess_cfg" ]] && grep -q "^  format: '<{DISPLAYNAME}> {MESSAGE}'" "$ess_cfg"; then
  cp "$ess_cfg" "$ess_cfg.bak"
  sed -i "s|^  format: '<{DISPLAYNAME}> {MESSAGE}'|  format: '{PREFIX}{DISPLAYNAME}\&7: \&f{MESSAGE}'|" "$ess_cfg"
  mc-cmd "essentials reload"
  echo "Format du chat mis à jour (copie de sauvegarde : config.yml.bak)."
else
  echo "Format du chat non modifié (déjà personnalisé ou introuvable)."
fi

echo
echo "Terminé. Pour donner un grade : mc-cmd \"lp user <Pseudo> parent set vip\" (ou modo / admin / default)"
