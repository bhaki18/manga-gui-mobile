# 📱 Piano di Lavoro e Istruzioni per l'Agente AI: Versione Android di Manga GUI

> **NOTA FONDAMENTALE PER L'AGENTE**:
> Il progetto **PC (Linux & Windows)** presente nella root di questa cartella è **perfetto, completo e funzionante al 100%**. **NON modificare, rompere o rimuovere i file della versione PC** (`Views/`, `ViewModels/`, `Services/`, `Models/`, `cli/`, `build-packages.sh`, `publish-gh-pages.sh`, `PKGBUILD`).
> Il tuo unico obiettivo è creare una **versione nativa per Android** di **Manga GUI** all'interno di una sottocartella dedicata (es. `./android/`) basandoti interamente sulle funzionalità, lo stile visivo e i provider già presenti nella versione PC!

---

## 🔒 Regole Rigorose del Progetto (DA RISPETTARE SEMPRE)

1. **Confinamento alla Cartella di Lavoro Corrente**:
   - Opera e confina ogni lettura, scrittura, esecuzione ed ispezione rigorosamente all'interno della cartella di lavoro (`/home/adp/Desktop/manga-gui`) e delle sue sotto-cartelle (es. `android/`, `cli/`).
   - È vietato uscire dalla cartella di lavoro per accedere, leggere o modificare file personali o percorsi esterni arbitrari del sistema.
2. **Nessuna Operazione Git Autonoma**:
   - Non eseguire **mai** in autonomia comandi di `git add`, `git commit` né `git push`.
   - Tutte le operazioni di staging, commit e sincronizzazione con i repository remoti spettano esclusivamente all'utente.
3. **Preservazione della Versione PC**:
   - Tutto il codice della versione Android deve risiedere nella sottocartella `./android/` per mantenere pulita e intatta la versione Desktop.

---

## 🛠️ Stack Tecnologico per Android (Kotlin + Jetpack Compose)

*(Nota: Swift è il linguaggio per iOS/Apple; per **Android** il linguaggio ufficiale e moderno è **Kotlin** con **Jetpack Compose**).*

- **Linguaggio**: **Kotlin**
- **UI Framework**: **Jetpack Compose** (Material 3 con tema scuro personalizzato identico a Manga GUI Desktop)
- **Architettura**: **MVVM** (`ViewModel`, `StateFlow`, `Coroutines`) — speculare a `MainViewModel.cs` del progetto PC
- **Networking & Scraping**:
  - `OkHttp` + `Jsoup` (equivalente di `curl` + `cheerio` usati in `cli/src/providers/`) + `Kotlinx Serialization` / `Gson`
- **Video Player (Streaming Anime & Riproduzione Offline)**:
  - **Media3 / ExoPlayer** (con supporto nativo a stream diretti `.mp4` e `.m3u8` HLS e header personalizzati `Referer` e `User-Agent`, esattamente come fa MPV su PC)
- **Manga Reader (Lettura Capitoli Online & Offline)**:
  - Lettore verticale/orizzontale con zoom pinch-to-zoom (`LazyColumn` / `HorizontalPager` con `Coil` per il caricamento delle immagini e header `Referer`)
- **Immagini / Poster**:
  - **Coil** (`AsyncImage`) integrato con le API di **Jikan / MyAnimeList** o gli URL cover estratti dai provider (vedi `Services/CoverService.cs`)

---

## 🎨 Design e Stile Visivo (Identico alla Versione PC)

Prendi come riferimento [Views/MainWindow.axaml](file:///home/adp/Desktop/manga-gui/Views/MainWindow.axaml):
- **Palette Colori (Crunchyroll / Fluent Dark Style)**:
  - Sfondo principale: `#0A0A0C` / `#0F1014`
  - Superfici / Card / Pannelli: `#18191E` con bordo `#2C2D35`
  - Colore d'accento primario: **Arancione `#FF640A`**
  - Testo primario: `#FFFFFF`, Testo secondario: `#A0A0A0`
- **Navigazione Principale (Bottom Navigation Bar su Android)**:
  1. 🎬 **Anime** (Catalogo Popolari + Ricerca + Filtri)
  2. 📖 **Manga** (Catalogo Popolari + Ricerca + Filtri)
  3. ⬇ **Downloaded** (Libreria Offline salvata nella memoria dell'app)
  4. 📜 **History** (Cronologia episodi visti e capitoli letti)

---

## ⚙️ Funzionalità del Progetto PC da Replicare su Android

Prima di scrivere il codice Android, ispeziona questi file di riferimento della versione PC per replicarne fedelmente tutta la logica:

### 1. Fonti (Providers) da Portare in Kotlin
Tutti gli scraper si trovano già pronti e funzionanti in JavaScript dentro `cli/src/providers/`. Traduci la loro logica HTTP/HTML in classi Kotlin (`AnimeProvider` e `MangaProvider`):
- **Anime Providers**:
  - `AnimeWorld` ([`cli/src/providers/community/animeworld.js`](file:///home/adp/Desktop/manga-gui/cli/src/providers/community/animeworld.js)) — Estrazione MP4 diretto da `animeworld.ac`
  - `AnimeSaturn` ([`cli/src/providers/community/animesaturn.js`](file:///home/adp/Desktop/manga-gui/cli/src/providers/community/animesaturn.js)) — Estrazione MP4/HLS da `animesaturn.net`
  - `AnimeUnity` ([`cli/src/providers/community/animeunity.js`](file:///home/adp/Desktop/manga-gui/cli/src/providers/community/animeunity.js)) — Stream 1080p tramite `vixcloud.cc` (StreamingCommunity CDN)
  - `One Piece Power Anime` ([`cli/src/providers/onepiecepower-anime.js`](file:///home/adp/Desktop/manga-gui/cli/src/providers/onepiecepower-anime.js)) — Stream da `onepiecepower.com`
- **Manga Providers**:
  - `One Piece Power` ([`cli/src/providers/onepiecepower.js`](file:///home/adp/Desktop/manga-gui/cli/src/providers/onepiecepower.js))
  - `MangaWorld` ([`cli/src/providers/community/mangaworld.js`](file:///home/adp/Desktop/manga-gui/cli/src/providers/community/mangaworld.js))
  - `MangaDex` ([`cli/src/providers/mangadex.js`](file:///home/adp/Desktop/manga-gui/cli/src/providers/mangadex.js))
  - `Mangapill` ([`cli/src/providers/mangapill.js`](file:///home/adp/Desktop/manga-gui/cli/src/providers/mangapill.js))
- **Interleaving `"All sources"`**:
  - Quando è selezionato `"All sources"`, alterna i risultati (round-robin) tra tutti i provider attivi esattamente come fa [`cli/src/providers/manager.js`](file:///home/adp/Desktop/manga-gui/cli/src/providers/manager.js).

### 2. Selettore Lingua & Filtri Dinamici (Stile, Genere, Sub/Dub)
Riferimento: [`ViewModels/MainViewModel.cs`](file:///home/adp/Desktop/manga-gui/ViewModels/MainViewModel.cs) e [`cli/src/utils/filters.js`](file:///home/adp/Desktop/manga-gui/cli/src/utils/filters.js):
- **Selettore Lingua**: `All languages`, `Italiano (IT)`, `English (EN)`, `Español (ES)`, `Français (FR)`, `Deutsch (DE)`, `Português (PT-BR)`, ecc.
- **Filtro Stile / Genere**: `All genres`, `Shounen`, `Seinen`, `Isekai`, `Action (Azione)`, `Adventure`, `Fantasy`, `Comedy`, `Romance`, `Drama`, `Sci-Fi`, `Horror`, `Mystery`, `Supernatural`, `Slice of Life`, `Sports`, `Psychological`, `Thriller`, `Ecchi`, `Mecha`, `Martial Arts`, `School`, `Historical`, `Shoujo` + chip rapidi cliccabili.
- **Filtro Audio Sub / Dub Dinamico in base alla Lingua**:
  - Il filtro Sub/Dub si adatta dinamicamente alla lingua selezionata (es. `🇮🇹 Solo Sub IT` / `🇮🇹 Solo Dub IT` se la lingua è Italiano, `🇬🇧 Solo Sub EN` / `🇬🇧 Solo Dub EN` se è Inglese, `💬 Solo Sub` / `🎙️ Solo Dub` se è `All languages`).

### 3. Cronologia di Ricerca Rapida (Ultimi 5 Titoli)
Riferimento: [`Services/MangaCliService.cs`](file:///home/adp/Desktop/manga-gui/Services/MangaCliService.cs):
- Quando l'utente tocca/mette il focus sulla barra di ricerca, mostra un menu a tendina con gli **ultimi 5 anime/manga cercati**, con possibilità di cliccarli per cercare subito, rimuoverne uno (`✕`) o svuotare la lista (`Clear`).

### 4. Scheda Dettagli, Player, Reader, Download Offline e Cronologia
- Selezionando una card si apre un BottomSheet / schermata dettagli con:
  - Selettore Episodio / Capitolo (`-` / `+` e input diretto) con indicazione del numero massimo totale.
  - Badge `✔ WATCHED` / `✔ READ` se l'episodio o capitolo è già stato visto/letto, e pulsante `✕ Unmark` per rimuoverlo.
  - Pulsante **`▶ START WATCHING`** / **`📖 READ NOW`** (o `PLAY OFFLINE` / `READ OFFLINE`).
  - Pulsante **`⬇ DOWNLOAD`** con percentuale di avanzamento per salvare episodi `.mp4` e capitoli manga nella memoria locale del telefono.

### 5. Auto-Updater Android (`.apk`)
Riferimento: [`Services/UpdateService.cs`](file:///home/adp/Desktop/manga-gui/Services/UpdateService.cs) e [`version.json`](file:///home/adp/Desktop/manga-gui/version.json):
- All'avvio dell'app Android, controlla `version.json` online e confrontalo con `BuildConfig.VERSION_NAME`.
- **Solo se la versione online è strettamente più recente (`>`)**, mostra un dialogo che propone di scaricare e installare il nuovo pacchetto `.apk` aggiornato.

---

## 🚀 Ordine dei Passi Consigliato per Gemini 3.8 Flash

1. **Analisi Rapida**: Leggi [`cli/src/utils/filters.js`](file:///home/adp/Desktop/manga-gui/cli/src/utils/filters.js) e i provider in `cli/src/providers/` senza modificare alcun file esistente.
2. **Scaffolding Progetto Android**: Crea la struttura completa del progetto Gradle Kotlin + Jetpack Compose dentro `./android/` (`settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `AndroidManifest.xml`).
3. **Porting dei Provider e Modelli Dati**: Implementa in Kotlin i modelli (`MediaItem`, `Episode`, `Chapter`) e tutti gli 8 provider (`AnimeWorld`, `AnimeSaturn`, `AnimeUnity`, `OnePiecePowerAnime`, `OnePiecePower`, `MangaWorld`, `MangaDex`, `Mangapill`) più `CoverService` e `UpdateService`.
4. **UI Jetpack Compose**: Implementa la schermata principale con tema scuro `#0A0A0C` / `#FF640A`, barra di ricerca con popup delle ultime 5 ricerche, filtri Genere + Sub/Dub dinamici per lingua, griglia poster, Player video ExoPlayer e Reader Manga.
5. **Script di Build APK**: Aggiungi uno script `./build-android.sh` (e ignorato in `.gitignore` se necessario) per compilare facilmente l'APK (`./gradlew assembleRelease` / `assembleDebug`).

