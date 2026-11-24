# Enjoy Cube – Scanner smartphone (Android)

App per tablet Android 9+: splash con logo → 2 pagine di presentazione → START →
scansione via cavo USB di uno smartphone → report PDF.

## Ottenere l'APK con GitHub (gratis, nessun programma da installare)
1. Crea un account su https://github.com e un nuovo repository (es. "enjoycube-scanner").
2. Nel repository: "Add file" › "Upload files" e trascina TUTTO il contenuto di questa
   cartella (compresa la cartella nascosta `.github`). Premi "Commit changes".
   Suggerimento: se la cartella `.github` non viene caricata trascinando, crea il file a mano con
   "Add file › Create new file", nome `.github/workflows/build-apk.yml`, incollando il contenuto.
3. Apri la scheda "Actions": parte "Compila APK" (circa 5 minuti).
4. A compilazione finita (spunta verde) apri l'esecuzione e scarica "EnjoyCube-Scanner-APK"
   in fondo alla pagina: è uno ZIP che contiene `EnjoyCube-Scanner.apk`.

In alternativa: apri la cartella con Android Studio e usa Build › Build APK(s).

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
