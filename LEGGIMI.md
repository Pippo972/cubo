# Enjoy Cube – Scanner smartphone (Android)

App per tablet Android 9+: logo → 2 pagine di presentazione → START →
scansione via cavo USB di uno smartphone → report PDF.

## Compilare con Android Studio
Serve Android Studio **Koala (2024.1)** o più recente.

1. Estrai lo ZIP in una cartella (es. `C:\Progetti\EnjoyCubeScanner`).
2. Android Studio › **File › Open** › seleziona la cartella `EnjoyCubeScanner` › OK.
   Se chiede "Trust project", rispondi **Trust**.
3. Attendi la sincronizzazione Gradle (la prima volta scarica i componenti: qualche minuto).
   Se propone di installare l'SDK Android 34, accetta.
4. Menu **Build › Build App Bundle(s) / APK(s) › Build APK(s)**.
5. A fine compilazione clicca **locate** nel messaggio in basso a destra:
   l'APK è in `app/build/outputs/apk/debug/app-debug.apk`.

Puoi anche collegare il tablet al PC (con il Debug USB attivo sul tablet) e premere
il pulsante verde ▶ **Run** per installarla direttamente.

### APK "release" firmato (facoltativo)
Menu **Build › Generate Signed App Bundle / APK › APK**, crea una nuova keystore
(conservala: serve per tutti gli aggiornamenti futuri), scegli **release**.

## Installazione sul tablet
Copia l'APK sul tablet, aprilo e consenti "Installa app sconosciute" quando richiesto.

## Uso
1. Smartphone da analizzare: Impostazioni › Info telefono › tocca 7 volte "Numero build",
   poi Impostazioni › Opzioni sviluppatore › attiva "Debug USB".
2. Collega lo smartphone al tablet: l'adattatore/cavo OTG va dal lato del TABLET.
3. Nell'app premi START, poi "AVVIA SCANSIONE".
4. Sul tablet conferma l'accesso USB; sullo smartphone conferma "Consenti debug USB"
   (spunta "Consenti sempre" per non doverlo rifare).
5. Il report PDF viene salvato in
   `Android/data/it.enjoycube.scanner/files/Documents/` e si può aprire o condividere.

## Dati rilevati
Produttore, modello, numero di serie, versione Android e patch di sicurezza, stato bootloader,
batteria (livello, stato, salute, tensione, temperatura, tecnologia), RAM, memoria interna
(totale/usata/libera), risoluzione schermo, stato SIM e operatore.
Tutto in sola lettura: nessun dato personale viene letto o modificato.

## Struttura
- `app/src/main/java/it/enjoycube/scanner/` – schermate, analisi dati, report PDF
- `app/src/main/java/it/enjoycube/scanner/adb/` – comunicazione ADB via USB
- `app/src/main/res/drawable-nodpi/` – logo e pagine del PDF
