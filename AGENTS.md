# Regole del Progetto e Vincoli di Comportamento

## 1. Confinamento alla Cartella di Lavoro Corrente
- L'assistente deve operare e confinare ogni lettura, scrittura, esecuzione ed ispezione rigorosamente all'interno della cartella di lavoro (working directory) del progetto attivo e delle sue sotto-cartelle (es. sotto-moduli o cartelle interne come `cli/`).
- L'assistente deve operare e confinare ogni lettura, scrittura, esecuzione ed ispezione rigorosamente all'interno della cartella di lavoro (working directory) del progetto attivo e delle sue sotto-cartelle (es. sotto-moduli o cartelle interne come `cli/` o `android/`).
- È vietato uscire dalla cartella di lavoro per accedere, leggere o modificare file personali o percorsi esterni arbitrari del sistema.
- PUOI MODIFICARE SOLO LA CARTELLA manga-gui-mobile/ E LE SUE SUBCARTELLE, MA NIENT'ALTRO

## 2. Nessuna Operazione Git Autonoma
- L'assistente non deve mai eseguire in autonomia comandi di `git add`, `git commit` né `git push`.
- Tutte le operazioni di staging, commit e sincronizzazione con i repository remoti spettano esclusivamente all'utente.

## 3. Obiettivo Attuale: Creazione Versione Android (Preservando la Versione PC)
- Il progetto **PC (Linux & Windows)** è completo e funzionante al 100%: **non modificare né rompere i file della versione PC**.
- Per istruzioni dettagliate su come sviluppare la versione **Android** (in **Kotlin + Jetpack Compose** dentro `./android/`) replicando tutte le funzionalità, i provider, i filtri e il design della versione PC, consulta e segui rigorosamente il file [`agent.md`](file:///home/adp/Desktop/manga-gui/agent.md).
