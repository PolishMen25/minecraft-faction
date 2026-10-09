#!/usr/bin/env bash
# =============================================================================
#  Installation d'un serveur Minecraft PvP Faction (Paper + PvP 1.8)
#  Cible : Debian 12/13 (VM ou conteneur LXC Proxmox), lancé en root.
#
#  Relançable sans risque : les configs existantes ne sont jamais écrasées,
#  seuls le jar Paper et les plugins sont remis à jour.
#
#  Variables surchargeables :
#    MC_VERSION=26.2   version Minecraft/Paper
#    RAM=5G            mémoire allouée à la JVM (Xms = Xmx)
#    JAVA_VERSION=25   version de Java (Temurin)
#    MC_DIR=/opt/minecraft
#    EULA=true         accepte l'EULA Mojang sans poser la question
#
#  Exemple : MC_VERSION=26.2 RAM=6G ./install.sh
# =============================================================================
set -euo pipefail

MC_VERSION="${MC_VERSION:-26.2}"
RAM="${RAM:-5G}"
JAVA_VERSION="${JAVA_VERSION:-25}"
MC_DIR="${MC_DIR:-/opt/minecraft}"
MC_USER="minecraft"
SERVICE="minecraft"
UA="minecraft-faction-kit/1.0 (github.com/PolishMen25/minecraft-faction)"
KIT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Plugins : nom | slug Modrinth | dépôt GitHub (repli) | regex du jar GitHub
# Laisser un champ vide pour ignorer cette source.
PLUGINS=(
  "ViaVersion|viaversion|ViaVersion/ViaVersion|^ViaVersion-[0-9.]+\.jar$"
  "ViaBackwards|viabackwards|ViaVersion/ViaBackwards|^ViaBackwards-[0-9.]+\.jar$"
  "ViaRewind|viarewind|ViaVersion/ViaRewind|^ViaRewind-[0-9.]+\.jar$"
  "OldCombatMechanics|oldcombatmechanics|kernitus/OldCombatMechanics|^OldCombatMechanics.*\.jar$"
  "LuckPerms|luckperms||"
  "Vault||MilkBowl/Vault|^Vault\.jar$"
  "EssentialsX||EssentialsX/Essentials|^EssentialsX-[0-9][^-]*\.jar$"
  "EssentialsXSpawn||EssentialsX/Essentials|^EssentialsXSpawn-.*\.jar$"
  "EssentialsXChat||EssentialsX/Essentials|^EssentialsXChat-.*\.jar$"
  "FactionsUUID|factionsuuid|drtshock/Factions|^Factions.*\.jar$"
)

# ----------------------------------------------------------------------------
c_ok()   { printf '\e[32m[OK]\e[0m %s\n' "$*"; }
c_info() { printf '\e[36m[..]\e[0m %s\n' "$*"; }
c_warn() { printf '\e[33m[!!]\e[0m %s\n' "$*"; }
die()    { printf '\e[31m[ERREUR]\e[0m %s\n' "$*" >&2; exit 1; }

[[ $EUID -eq 0 ]] || die "Lance ce script en root (sudo ./install.sh)."
[[ -r /etc/debian_version ]] || die "Ce script cible Debian."

# ----------------------------------------------------------------------------
# 1. Paquets système + Java
# ----------------------------------------------------------------------------
c_info "Installation des paquets de base..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq curl jq tmux ca-certificates gnupg zstd >/dev/null

if ! java -version 2>&1 | grep -qE "version \"${JAVA_VERSION}[.\"]"; then
  c_info "Installation de Java ${JAVA_VERSION} (Eclipse Temurin)..."
  install -d -m 0755 /etc/apt/keyrings
  curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public \
    | gpg --dearmor --yes -o /etc/apt/keyrings/adoptium.gpg
  codename="$(. /etc/os-release && echo "$VERSION_CODENAME")"
  echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb ${codename} main" \
    > /etc/apt/sources.list.d/adoptium.list
  apt-get update -qq
  apt-get install -y -qq "temurin-${JAVA_VERSION}-jre" >/dev/null \
    || apt-get install -y -qq "temurin-${JAVA_VERSION}-jdk" >/dev/null \
    || die "Impossible d'installer Temurin ${JAVA_VERSION}."
fi
JAVA_BIN="$(command -v java)"
c_ok "Java : $("$JAVA_BIN" -version 2>&1 | head -1)"

# ----------------------------------------------------------------------------
# 2. Utilisateur et dossiers
# ----------------------------------------------------------------------------
if ! id "$MC_USER" &>/dev/null; then
  useradd --system --home-dir "$MC_DIR" --shell /bin/bash "$MC_USER"
fi
install -d -o "$MC_USER" -g "$MC_USER" "$MC_DIR" "$MC_DIR/plugins" "$MC_DIR/backups"

# ----------------------------------------------------------------------------
# 3. Paper (API Fill v3, build STABLE le plus récent, sha256 vérifié)
# ----------------------------------------------------------------------------
c_info "Recherche du dernier build Paper ${MC_VERSION}..."
builds="$(curl -fsSL -A "$UA" "https://fill.papermc.io/v3/projects/paper/versions/${MC_VERSION}/builds")" \
  || die "Version Paper ${MC_VERSION} introuvable. Vérifie sur https://papermc.io/downloads/paper"
build="$(jq -c '[.[] | select(.channel == "STABLE")] | max_by(.id) // empty' <<<"$builds")"
if [[ -z "$build" ]]; then
  c_warn "Aucun build STABLE pour ${MC_VERSION}, utilisation du build le plus récent (bêta)."
  build="$(jq -c 'max_by(.id)' <<<"$builds")"
fi
paper_url="$(jq -r '.downloads["server:default"].url' <<<"$build")"
paper_sha="$(jq -r '.downloads["server:default"].checksums.sha256' <<<"$build")"
paper_id="$(jq -r '.id' <<<"$build")"

if [[ -f "$MC_DIR/paper.jar" ]] && echo "$paper_sha  $MC_DIR/paper.jar" | sha256sum -c --status; then
  c_ok "Paper ${MC_VERSION} build #${paper_id} déjà à jour."
else
  curl -fsSL -A "$UA" -o "$MC_DIR/paper.jar.tmp" "$paper_url"
  echo "$paper_sha  $MC_DIR/paper.jar.tmp" | sha256sum -c --status || die "Checksum Paper invalide."
  mv "$MC_DIR/paper.jar.tmp" "$MC_DIR/paper.jar"
  c_ok "Paper ${MC_VERSION} build #${paper_id} téléchargé."
fi

# ----------------------------------------------------------------------------
# 4. Plugins (Modrinth en priorité, GitHub Releases en repli)
# ----------------------------------------------------------------------------
# Renvoie "url sha512" du jar Modrinth le plus récent compatible, ou rien.
modrinth_latest() {
  local slug="$1" gv="$2" json
  json="$(curl -fsSL -A "$UA" -G "https://api.modrinth.com/v2/project/${slug}/version" \
      --data-urlencode 'loaders=["paper","spigot","bukkit"]' \
      ${gv:+--data-urlencode "game_versions=[\"$gv\"]"} 2>/dev/null)" || return 0
  jq -r 'map(select(.version_type == "release")) + map(select(.version_type != "release"))
         | .[0].files // [] | (map(select(.primary)) + .)[0] // empty
         | "\(.url) \(.hashes.sha512)"' <<<"$json"
}

github_latest() {
  local repo="$1" regex="$2"
  curl -fsSL -A "$UA" "https://api.github.com/repos/${repo}/releases/latest" 2>/dev/null \
    | jq -r --arg re "$regex" '.assets[] | select(.name | test($re)) | .browser_download_url' \
    | head -1
}

FAILED=()
for entry in "${PLUGINS[@]}"; do
  IFS='|' read -r name slug repo regex <<<"$entry"
  url="" sha=""
  if [[ -n "$slug" ]]; then
    read -r url sha < <(modrinth_latest "$slug" "$MC_VERSION"; echo) || true
    if [[ -z "$url" ]]; then
      read -r url sha < <(modrinth_latest "$slug" ""; echo) || true
      [[ -n "$url" ]] && c_warn "$name : pas de version marquée ${MC_VERSION} sur Modrinth, prise de la plus récente."
    fi
  fi
  if [[ -z "$url" && -n "$repo" ]]; then
    url="$(github_latest "$repo" "$regex" || true)"
    sha=""
  fi
  if [[ -z "$url" ]]; then
    c_warn "$name : introuvable automatiquement, à installer à la main dans $MC_DIR/plugins/"
    FAILED+=("$name")
    continue
  fi

  tmp="$(mktemp)"
  if ! curl -fsSL -A "$UA" -o "$tmp" "$url"; then
    c_warn "$name : échec du téléchargement ($url)"; FAILED+=("$name"); rm -f "$tmp"; continue
  fi
  if [[ -n "$sha" && "$sha" != "null" ]] && ! echo "$sha  $tmp" | sha512sum -c --status; then
    c_warn "$name : checksum invalide, ignoré."; FAILED+=("$name"); rm -f "$tmp"; continue
  fi
  # Nom fixe par plugin : une mise à jour remplace simplement l'ancien jar
  install -o "$MC_USER" -g "$MC_USER" -m 0644 "$tmp" "$MC_DIR/plugins/${name}.jar"
  rm -f "$tmp"
  c_ok "$name ← $(basename "${url%%\?*}")"
done

# ----------------------------------------------------------------------------
# 5. Configuration initiale (jamais écrasée si elle existe déjà)
# ----------------------------------------------------------------------------
copy_if_absent() {
  local src="$1" dst="$2"
  if [[ -e "$dst" ]]; then
    c_info "$(basename "$dst") existe déjà, conservé."
  else
    install -D -o "$MC_USER" -g "$MC_USER" -m 0644 "$src" "$dst"
    c_ok "$(basename "$dst") installé."
  fi
}
copy_if_absent "$KIT_DIR/config/server.properties" "$MC_DIR/server.properties"
copy_if_absent "$KIT_DIR/config/ops.json.example"   "$MC_DIR/ops.json.example"

if [[ ! -f "$MC_DIR/eula.txt" ]] || ! grep -q '^eula=true' "$MC_DIR/eula.txt"; then
  if [[ "${EULA:-}" != "true" ]]; then
    echo
    echo "Le serveur exige d'accepter l'EULA Minecraft : https://aka.ms/MinecraftEULA"
    read -r -p "Acceptes-tu l'EULA ? [o/N] " ans
    [[ "$ans" =~ ^[oOyY]$ ]] || die "EULA refusée, installation interrompue."
  fi
  echo "eula=true" > "$MC_DIR/eula.txt"
  chown "$MC_USER:$MC_USER" "$MC_DIR/eula.txt"
fi

# Script de lancement (flags Aikar, régénéré à chaque installation pour suivre RAM)
cat > "$MC_DIR/start.sh" <<EOF
#!/usr/bin/env bash
cd "$MC_DIR"
exec "$JAVA_BIN" -Xms${RAM} -Xmx${RAM} \\
  -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 \\
  -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch \\
  -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M \\
  -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 \\
  -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 \\
  -XX:G1RSetUpdatingPauseIntervalMillis=0 -XX:SurvivorRatio=32 \\
  -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 \\
  -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true \\
  -jar paper.jar --nogui
EOF
chmod 0755 "$MC_DIR/start.sh"
chown "$MC_USER:$MC_USER" "$MC_DIR/start.sh"

# Outils d'administration
install -m 0755 "$KIT_DIR/scripts/mc-console" /usr/local/bin/mc-console
install -m 0755 "$KIT_DIR/scripts/mc-cmd"     /usr/local/bin/mc-cmd
install -m 0755 "$KIT_DIR/scripts/mc-backup"  /usr/local/bin/mc-backup

# ----------------------------------------------------------------------------
# 6. Services systemd (serveur + sauvegarde quotidienne)
# ----------------------------------------------------------------------------
cat > "/etc/systemd/system/${SERVICE}.service" <<EOF
[Unit]
Description=Serveur Minecraft Faction (Paper ${MC_VERSION})
After=network-online.target
Wants=network-online.target

[Service]
Type=forking
User=${MC_USER}
Group=${MC_USER}
WorkingDirectory=${MC_DIR}
ExecStart=/usr/bin/tmux new-session -d -s mc ${MC_DIR}/start.sh
ExecStop=/usr/bin/tmux send-keys -t mc "say Arrêt du serveur dans 5 secondes..." Enter
ExecStop=/bin/sleep 5
ExecStop=/usr/bin/tmux send-keys -t mc "stop" Enter
ExecStop=/bin/bash -c 'while /usr/bin/tmux has-session -t mc 2>/dev/null; do sleep 1; done'
TimeoutStopSec=90
Restart=on-failure
RestartSec=15

[Install]
WantedBy=multi-user.target
EOF

cat > /etc/systemd/system/minecraft-backup.service <<EOF
[Unit]
Description=Sauvegarde du serveur Minecraft

[Service]
Type=oneshot
ExecStart=/usr/local/bin/mc-backup
EOF

cat > /etc/systemd/system/minecraft-backup.timer <<EOF
[Unit]
Description=Sauvegarde quotidienne du serveur Minecraft

[Timer]
OnCalendar=*-*-* 05:00:00
Persistent=true

[Install]
WantedBy=timers.target
EOF

systemctl daemon-reload
systemctl enable --now minecraft-backup.timer >/dev/null
systemctl enable "$SERVICE" >/dev/null
if systemctl is-active --quiet "$SERVICE"; then
  c_info "Le serveur tourne déjà : redémarrage pour appliquer les mises à jour..."
  systemctl restart "$SERVICE"
else
  systemctl start "$SERVICE"
fi

# ----------------------------------------------------------------------------
echo
c_ok "Installation terminée. Le premier démarrage génère le monde (1 à 3 min)."
echo
echo "  Console du serveur  : mc-console      (quitter sans arrêter : Ctrl+B puis D)"
echo "  Commande ponctuelle : mc-cmd \"whitelist add Pseudo\""
echo "  Logs                : tail -f $MC_DIR/logs/latest.log"
echo "  Sauvegarde manuelle : mc-backup"
echo
if ((${#FAILED[@]})); then
  c_warn "Plugins à installer à la main : ${FAILED[*]}"
  echo "     Dépose les .jar dans $MC_DIR/plugins/ puis : systemctl restart $SERVICE"
fi
