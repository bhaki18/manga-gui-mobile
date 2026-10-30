# Manga GUI Mobile (Android)

Versione nativa Android di **Manga GUI**, sviluppata in **Kotlin + Jetpack Compose**, che replica tutte le funzionalità, i provider e il tema della versione desktop per PC.

---

## 📱 Funzionalità Principali

1. **Catalogo & Ricerca Globale**
   - Streaming Anime e Lettura Manga multi-sorgente con round-robin interleaving.
   - Cronologia ricerche recenti: dropdown interattivo con le ultime 5 ricerche effettuate, pulsante di cancellazione rapida del singolo elemento o di tutta la cronologia.

2. **Filtri Completi & Dinamici**
   - Filtro Stile/Genere (Shounen, Seinen, Isekai, Azione, Commedia, Romance, Horror, Fantasy, ecc.).
   - Filtro SUB / DUB dinamico in base alla lingua selezionata (es. DUB ITA / SUB ITA per Italiano, DUB ENG / SUB ENG per Inglese, ecc.).
   - Selettore Lingua per cataloghi e sorgenti internazionali (MangaDex, Mangapill, AnimeWorld, AnimeSaturn, MangaWorld, OnePiecePower).

3. **Player Video Nativo & Lettore Manga**
   - **Player**: basato su Jetpack Media3 (ExoPlayer), supporto streaming MP4 ed HLS (.m3u8), intestazioni HTTP/Referer personalizzate (VixCloud/SaturnCDN), controlli touch e rotazione automatica landscape.
   - **Lettore Manga**: lettura continua a scorrimento verticale (webtoon strip), precaricamento immagini asincrono con Coil, indicatore pagina/totale, pulsanti salto capitolo precedente/successivo e overlay touch a scomparsa.

4. **Scaricati Offline & Cronologia**
   - Download in locale di episodi MP4 e capitoli completi di manga.
   - Visualizzazione dimensione in MB/GB e gestione eliminazione.
   - Cronologia visualizzazioni e letture (`history.json`) con calcolo dei range di episodi visti (es. `1-12`, `1-1050`), indicatore visto/letto e possibilità di deselezionare.

5. **In-App Updater**
   - Controllo all'avvio con `version.json` sul repository remoto.
   - Notifica solo se la versione online è strettamente più recente (`newer`).
   - Download progressivo del file APK con barra di avanzamento e richiesta di installazione automatica tramite `FileProvider`.

---

## 🛠️ Come Compilare ed Eseguire

### Opzione 1: Con Android Studio (Consigliato)
1. Apri **Android Studio**.
2. Seleziona **Open** e scegli questa cartella (`manga-gui-mobile`).
3. Attendi la sincronizzazione di Gradle.
4. Collega un dispositivo Android (o avvia un emulatore) e premi **Run (Shift+F10)**.

### Opzione 2: Da Riga di Comando (CLI)
Assicurati di avere installato un JDK (versione 17 o 21) e l'Android SDK:
```bash
./build.sh debug    # Compila l'APK di Debug
./build.sh release  # Compila l'APK Release
```
Oppure tramite Gradle wrapper:
```bash
./gradlew assembleDebug
```
L'APK generato sarà posizionato in `app/build/outputs/apk/release/app-release.apk`.

---

## 🚀 Pubblicazione e Auto-Updater su GitHub Pages

Per pubblicare l'APK e il manifesto `version_mobile.json` direttamente sul branch `gh-pages` di `manga-cli`:

```bash
# Pubblica l'APK release attuale
./publish-gh-pages.sh

# Oppure aggiorna versione, note di rilascio, compila e pubblica in un colpo solo:
./publish-gh-pages.sh --version 1.0.1 --notes "Risolto bug lettore e migliorato player video"
```
I dispositivi con l'app installata controlleranno automaticamente l'aggiornamento all'avvio da:
- Manifest: `https://bhaki18.github.io/manga-cli/version_mobile.json`
- APK Download: `https://bhaki18.github.io/manga-cli/android/app-release.apk`


---

## 📂 Struttura del Progetto

```
manga-gui-mobile/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/mangagui/app/
│   │   │   ├── MangaGuiApp.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── model/           # MediaItem, FilterModels, StreamData, etc.
│   │   │   ├── network/         # OkHttpClient con DNS-over-HTTPS & Jsoup
│   │   │   ├── provider/        # AnimeWorld, AnimeSaturn, AnimeUnity, OnePiecePower, MangaWorld, MangaDex, Mangapill
│   │   │   ├── service/         # HistoryService, SearchHistoryService, DownloadService, CoverService, UpdateService
│   │   │   └── ui/
│   │   │       ├── components/  # TopSearchBar, FilterSection, MediaCard, DetailsBottomSheet, BottomNavBar, UpdateDialog
│   │   │       ├── screens/     # MainScreen, PlayerScreen, ReaderScreen
│   │   │       └── theme/       # Dark Theme Crunchyroll (#0B0C0D, #F47521, etc.)
│   │   └── res/                 # Icone mipmap, layout xml, stili
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml       # Dipendenze centralizzate
├── build.gradle.kts
├── settings.gradle.kts
└── build.sh
```
