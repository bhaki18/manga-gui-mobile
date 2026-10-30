#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

REPO_URL="git@github.com:bhaki18/manga-cli.git"
BRANCH="gh-pages"
TMP_DIR="$SCRIPT_DIR/.gh-pages-tmp"
DO_BUILD=false
NEW_VERSION=""
CUSTOM_NOTES=""
CUSTOM_MSG=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --build|-b)
            DO_BUILD=true
            shift
            ;;
        --version|-v)
            NEW_VERSION="${2:-}"
            DO_BUILD=true
            shift 2
            ;;
        --notes|-n)
            CUSTOM_NOTES="${2:-}"
            shift 2
            ;;
        -m|--message)
            CUSTOM_MSG="${2:-}"
            shift 2
            ;;
        --help|-h)
            echo "Utilizzo: ./publish-gh-pages.sh [OPZIONI]"
            echo ""
            echo "Pubblica l'APK Android (app-release.apk) e version_mobile.json"
            echo "nel repository gh-pages di bhaki18/manga-cli.git per l'Auto-Updater Android."
            echo ""
            echo "Opzioni:"
            echo "  (nessuna)              Carica l'ultimo app-release.apk e version_mobile.json"
            echo "  --build, -b            Ricompila prima l'APK Release tramite ./build.sh release"
            echo "  --version, -v <ver>    Imposta la nuova versione (es. 1.0.1) in version_mobile.json e build.gradle.kts"
            echo "  --notes, -n <testo>    Imposta le note di rilascio mostrate nel popup dell'Updater su Android"
            echo "  -m, --message <msg>    Messaggio di commit personalizzato"
            echo "  --help, -h             Mostra questa guida"
            exit 0
            ;;
        *)
            echo "Opzione non riconosciuta: $1 (usa --help per la guida)"
            exit 1
            ;;
    esac
done

echo "============================================================"
echo "📱 MANGA-GUI-MOBILE -> GH-PAGES UPDATER PUBLISHER"
echo "   Repo:   $REPO_URL (branch: $BRANCH)"
echo "============================================================"

# 1. Eventuale aggiornamento versione
if [ -n "$NEW_VERSION" ]; then
    sed -i "s/\"version\": \".*\"/\"version\": \"${NEW_VERSION}\"/" version_mobile.json
    sed -i "s/\"pkgver\": \".*\"/\"pkgver\": \"${NEW_VERSION}\"/" version_mobile.json
    sed -i "s/versionName = \".*\"/versionName = \"${NEW_VERSION}\"/" app/build.gradle.kts
    echo "🔼 Impostata nuova versione: $NEW_VERSION"
fi

if [ -n "$CUSTOM_NOTES" ]; then
    if command -v node >/dev/null 2>&1; then
        node -e '
          const fs = require("fs");
          const v = JSON.parse(fs.readFileSync("./version_mobile.json", "utf8"));
          v.notes = process.argv[1];
          fs.writeFileSync("./version_mobile.json", JSON.stringify(v, null, 2) + "\n");
        ' "$CUSTOM_NOTES"
    fi
    echo "📝 Note di rilascio aggiornate in version_mobile.json"
fi

# 2. Eventuale compilazione Release
if [ "$DO_BUILD" = true ] || [ ! -f "app/build/outputs/apk/release/app-release.apk" ]; then
    echo "🔨 Compilazione APK Release in corso..."
    ./build.sh release
fi

APK_FILE="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK_FILE" ]; then
    echo "❌ Errore: file $APK_FILE non trovato."
    exit 1
fi

CURRENT_VER=$(grep -E '"version":' version_mobile.json | head -n 1 | awk -F '"' '{print $4}')
echo "📦 APK selezionato: $APK_FILE (versione: $CURRENT_VER)"

# 3. Clona gh-pages in cartella temporanea
cleanup() {
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT

rm -rf "$TMP_DIR"
echo "📥 Clonazione branch '$BRANCH' da $REPO_URL..."
git clone --depth 1 --branch "$BRANCH" "$REPO_URL" "$TMP_DIR"

# 4. Crea cartella android/ sul gh-pages e copia i file
mkdir -p "$TMP_DIR/android"
echo "🔄 Copia APK e version_mobile.json..."
cp -f "$APK_FILE" "$TMP_DIR/android/app-release.apk"
cp -f "$APK_FILE" "$TMP_DIR/android/manga-gui-$CURRENT_VER.apk"
cp -f "$SCRIPT_DIR/version_mobile.json" "$TMP_DIR/version_mobile.json"
cp -f "$SCRIPT_DIR/version_mobile.json" "$TMP_DIR/android/version_mobile.json"

# 5. Commit e Push
COMMIT_MSG="${CUSTOM_MSG:-Aggiornato Manga GUI Mobile alla versione $CURRENT_VER}"

(
    cd "$TMP_DIR"
    git add android/ version_mobile.json
    if git diff --cached --quiet; then
        echo "ℹ️ Nessuna modifica rilevata rispetto al branch '$BRANCH' remoto."
        exit 0
    fi
    git commit -m "$COMMIT_MSG"
    echo "🚀 Push su $REPO_URL (branch: $BRANCH)..."
    git push origin "$BRANCH"
)

echo ""
echo "============================================================"
echo "✅ PUBBLICAZIONE SU GH-PAGES COMPLETATA CON SUCCESSO!"
echo "   Endpoint Updater: https://bhaki18.github.io/manga-cli/version_mobile.json"
echo "   Download APK:     https://bhaki18.github.io/manga-cli/android/app-release.apk"
echo "============================================================"

