# Cartella TCG

App Android (Kotlin + Jetpack Compose) per tracciare la propria collezione di carte Pokémon, in qualsiasi lingua (italiano incluso), con valore stimato in base alla condizione.

## Cosa fa

- **Cerca** le carte per nome o sfogliando i set, nella lingua scelta (dati e immagini da [TCGdex](https://tcgdex.dev), che supporta più di 10 lingue tra cui l'italiano).
- **Collezione** salvata sul telefono (Room): ogni copia ha carta, **lingua**, **condizione** (Mint → Poor), **variante** (normale / reverse / holo / 1ª edizione) e quantità.
- **Valore stimato**: prezzo di riferimento Cardmarket (EUR), con ripiego su TCGplayer (USD), moltiplicato per una percentuale legata alla condizione. Le percentuali sono modificabili in Impostazioni.
- **Aggiorna prezzi**: riscarica i prezzi di tutta la collezione.

## Interfaccia

Tema personalizzato (chiaro e scuro) con testate a gradiente, selettore di lingua a tendina con bandiere, ricerche rapide, badge "×N" sulle carte già possedute, scheda carta con descrizione di ogni condizione, valore stimato in evidenza, collezione ordinabile (recenti, valore, nome) e messaggi di conferma.

## Collegamento con TCGdex

L'app legge carte, set, immagini e prezzi dall'API REST pubblica di TCGdex (`https://api.tcgdex.net/v2/{lingua}/...`, nessuna chiave richiesta). In Impostazioni ci sono i link a [tcgdex.dev](https://tcgdex.dev) e alla pagina di stato con le lingue disponibili. Il client è in `app/src/main/java/it/cartella/tcg/data/TcgdexApi.kt`.

## Limiti noti

- I prezzi sono le medie di mercato fornite da TCGdex, non i singoli "venduti" di eBay (l'API eBay per i venduti è ad accesso limitato e lo scraping è bloccato/vietato).
- Non è verificato che i prezzi distinguano tra lingue diverse della stessa carta.
- Il nome "Pokémon" è un marchio: prima di pubblicare su Google Play scegli un nome che non lo contenga e controlla le regole di Play sui contenuti con marchi di terzi.

## Compilare l'APK

Con GitHub Actions: carica il progetto in un repository, il workflow `.github/workflows/build-apk.yml` compila l'APK debug e lo pubblica come artifact ("Actions" → ultima esecuzione → `cartella-tcg-debug-apk`).

In locale (Android Studio, JDK 17): apri la cartella e premi Run.

## Struttura

- `data/` – client TCGdex, valutazione per condizione, database Room, impostazioni (DataStore)
- `AppViewModel.kt` – stato e logica
- `ui/` – schermate Compose (Cerca, Collezione, Impostazioni, dettaglio carta)

## Prossimi passi possibili

- Scanner con la fotocamera per riconoscere la carta
- Venduti reali eBay (se si ottiene l'accesso alla Marketplace Insights API) o aggregatore a pagamento
- Server che aggiorna i prezzi una volta al giorno
- Export/import CSV della collezione
