#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$SCRIPT_DIR"

echo "========================================="
echo "   Manga GUI - Android Build Tool"
echo "========================================="

# 1. Search for a compatible JDK (JDK 17 or 21)
COMPATIBLE_JDK=""

# Check known JDK 21 / 17 locations on Arch / Linux
CANDIDATE_JDKS=(
    "/usr/lib/jvm/java-21-openjdk"
    "/usr/lib/jvm/java-17-openjdk"
    "/opt/android-studio/jbr"
    "$HOME/.local/share/JetBrains/Toolbox/apps/android-studio/jbr"
    "/opt/android-studio/jre"
)

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    CURRENT_VER=$("$JAVA_HOME/bin/java" -version 2>&1 | awk -F '"' '/version/ {print $2}')
    MAJOR_VER=$(echo "$CURRENT_VER" | cut -d'.' -f1)
    if [ "$MAJOR_VER" -le 21 ] && [ "$MAJOR_VER" -ge 17 ]; then
        COMPATIBLE_JDK="$JAVA_HOME"
    fi
fi

if [ -z "$COMPATIBLE_JDK" ]; then
    for jdk in "${CANDIDATE_JDKS[@]}"; do
        if [ -d "$jdk" ] && [ -x "$jdk/bin/java" ]; then
            COMPATIBLE_JDK="$jdk"
            break
        fi
    done
fi

if [ -n "$COMPATIBLE_JDK" ]; then
    export JAVA_HOME="$COMPATIBLE_JDK"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "✔ JDK compatibile configurato: $JAVA_HOME"
else
    # Check default system java
    SYSTEM_JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
    MAJOR_VER=$(echo "$SYSTEM_JAVA_VER" | cut -d'.' -f1)
    
    if [ "$MAJOR_VER" -gt 21 ]; then
        echo "❌ Errore versione Java: rilevato Java $SYSTEM_JAVA_VER."
        echo "Gradle 8 e i tool Android non supportano Java $MAJOR_VER (supportano JDK 17 o JDK 21)."
        echo ""
        echo "👉 Per risolvere su Arch Linux, installa e imposta JDK 21:"
        echo "    sudo pacman -S jdk21-openjdk"
        echo "    sudo archlinux-java set java-21-openjdk"
        echo ""
        echo "Oppure apri il progetto direttamente con Android Studio (che include il proprio JDK 21)."
        exit 1
    fi
fi

JAVA_VERSION_STR=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
echo "ℹ️  Java in uso: $JAVA_VERSION_STR"

# 2. Check Android SDK
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    if [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    elif [ -d "/opt/android-sdk" ]; then
        export ANDROID_HOME="/opt/android-sdk"
    fi
fi

if [ -n "$ANDROID_HOME" ]; then
    echo "ℹ️  Android SDK: $ANDROID_HOME"
else
    echo "⚠️  Attenzione: Variabile ANDROID_HOME non impostata."
    echo "Se non hai l'Android SDK installato per la CLI, puoi:"
    echo "  1) Aprire la cartella 'manga-gui-mobile' con Android Studio (consigliato)."
    echo "  2) Oppure installare l'SDK Android da AUR (es. 'paru -S android-sdk')."
    echo ""
fi

cd "$ANDROID_DIR"

BUILD_TYPE="${1:-debug}"

if [ "$BUILD_TYPE" = "release" ]; then
    echo "🚀 Compilazione APK Release..."
    ./gradlew assembleRelease
    APK_PATH="$ANDROID_DIR/app/build/outputs/apk/release/app-release.apk"
else
    echo "🚀 Compilazione APK Debug..."
    ./gradlew assembleDebug
    APK_PATH="$ANDROID_DIR/app/build/outputs/apk/debug/app-debug.apk"
fi

if [ -f "$APK_PATH" ]; then
    echo ""
    echo "✅ Compilazione completata con successo!"
    echo "📦 APK generato in: $APK_PATH"
else
    echo "ℹ️  Build terminata."
fi
