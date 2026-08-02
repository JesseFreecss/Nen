#!/usr/bin/env bash
# Se déclenche (via PostToolUse/Bash) après une commande qui vient d'installer
# l'app sur le téléphone de test. HyperOS/MIUI purge la whitelist batterie et
# les appops arrière-plan à chaque `adb install -r`, ce qui finit par tuer le
# service d'accessibilité de Nen sans le relancer (pas de START_STICKY pour un
# AccessibilityService, contrairement au VpnService). Voir la mémoire
# nen-appareil-test-xiaomi.md pour le détail du diagnostic.
set -u

PACKAGE="com.jesse.nen"
SERVICE="$PACKAGE/$PACKAGE.accessibility.NenAccessibilityService"

input="$(cat)"
command="$(printf '%s' "$input" | node -e '
let d="";
process.stdin.on("data",c=>d+=c);
process.stdin.on("end",()=>{
  try { process.stdout.write(JSON.parse(d).tool_input.command || ""); } catch (e) {}
});
')"

case "$command" in
  *"adb install"*|*"gradlew"*"install"*) ;;
  *) exit 0 ;;
esac

if ! command -v adb >/dev/null 2>&1; then
  exit 0
fi
if [ -z "$(adb devices | awk 'NR>1 && $2=="device" {print; exit}')" ]; then
  exit 0
fi

export MSYS_NO_PATHCONV=1

adb shell cmd deviceidle whitelist "+$PACKAGE" >/dev/null 2>&1
adb shell cmd appops set "$PACKAGE" RUN_ANY_IN_BACKGROUND allow >/dev/null 2>&1
adb shell cmd appops set "$PACKAGE" RUN_IN_BACKGROUND allow >/dev/null 2>&1
adb shell settings delete secure enabled_accessibility_services >/dev/null 2>&1
adb shell settings put secure enabled_accessibility_services "$SERVICE" >/dev/null 2>&1
adb shell settings put secure accessibility_enabled 1 >/dev/null 2>&1

echo '{"systemMessage": "Nen: whitelist batterie + appops réappliqués, service d'\''accessibilité relancé après install."}'
