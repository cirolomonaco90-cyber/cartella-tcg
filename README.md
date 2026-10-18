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

## Prezzi per lingua (algoritmo)

Nessuna configurazione per chi usa l'app. I prezzi per lingua sono calcolati **ogni notte da GitHub Actions** (`.github/workflows/prezzi.yml`, gratis perché il repo è pubblico) e pubblicati come file JSON sul branch `prezzi`, da cui l'app li scarica e li tiene in cache:

```
https://raw.githubusercontent.com/cirolomonaco90-cyber/cartella-tcg/prezzi/v1/catalog.json
https://raw.githubusercontent.com/cirolomonaco90-cyber/cartella-tcg/prezzi/v1/<en|it|fr|de|es|pt>.json
```

Fonti, senza eBay:
- **Cardmarket**: prezzo affidabile ma uguale per tutte le lingue (listino pubblico, lo stesso usato da TCGdex). L'API di Cardmarket non accetta nuovi sviluppatori.
- **CardTrader**: API ufficiale, con **offerte filtrate per lingua**. Il job usa un solo token, salvato come secret del repo `CARDTRADER_TOKEN`. Le carte CardTrader sono abbinate a quelle dell'app tramite l'id del prodotto Cardmarket, che entrambi conoscono.

Il job (`prezzi/src/build.js`, circa un'ora a notte):
1. Per ogni espansione Pokémon su CardTrader legge le carte e i loro id Cardmarket.
2. Per ogni lingua scarica le offerte più economiche di tutta l'espansione con una sola richiesta, rispettando il limite di circa 1 richiesta al secondo.
3. Per ogni carta e variante (normale, reverse, 1ª edizione) calcola la stima Near Mint: esclude graded e lotti, riporta ogni offerta a NM con il coefficiente della condizione, scarta un'eventuale offerta anomala e prende la mediana delle 5 più economiche.
4. Calcola i **coefficienti di lingua** come mediana di (stima lingua / trend Cardmarket) sulle carte con almeno 3 offerte, per **set** (da 5 carte in su) e per lingua.

Nell'app (`data/LanguagePrices.kt`, `data/PriceEngine.kt`), per ogni copia:
`prezzo NM = peso × offerte lingua + (1 − peso) × Cardmarket × coefficiente (del set, altrimenti della lingua)`, con peso = offerte / 6 (al massimo 100%), poi × percentuale della condizione. Nella scheda di ogni carta c'è il dettaglio del calcolo.

**Attivazione (una volta, solo lo sviluppatore):** repo GitHub → Settings → Secrets and variables → Actions → New repository secret, nome `CARDTRADER_TOKEN`, valore il token API del proprio account CardTrader. Poi Actions → "Prezzi per lingua" → Run workflow.

## Come si calcola il prezzo di riferimento Cardmarket

`prezzo di riferimento × % condizione × % correzione lingua`

1. **Prezzo di riferimento** (Near Mint): prima il prezzo della variante specifica (`variants_detailed` di TCGdex, utile per 1ª edizione e reverse), poi il prezzo generale della carta. Cardmarket (EUR, `trend`, oppure `trend-holo` per le reverse), con ripiego su TCGplayer (USD, `marketPrice`). Se una variante non ha un prezzo suo, l'app usa quello base e lo segnala come approssimato.
2. **Condizione**: percentuale modificabile (default M 105%, NM 100%, EX 85%, GD 70%, LP 55%, PL 40%, PO 25%).
3. **Correzione lingua**: i prezzi TCGdex sono gli stessi per tutte le lingue, quindi ogni lingua ha una percentuale (default 100%) da regolare in Impostazioni.

Nella scheda di ogni carta, "Venduti su eBay" apre le inserzioni vendute su eBay.it per quella carta, per un controllo manuale.

## Copertura delle carte e ripiego sull'inglese

TCGdex ha quasi tutte le carte occidentali in inglese (verificato sul database sorgente a ottobre 2026: 21.291 carte su 21.393, set fino a settembre 2026). In italiano invece mancano quasi tutti i set 2000-2011 (Fossil, Team Rocket, Neo, e-Card, EX, Diamante e Perla, Platino, HeartGold SoulSilver, ...), circa il 32% delle carte.

Siccome l'id di una carta è uguale in tutte le lingue, l'app unisce i risultati: ricerca, elenco dei set e carte di un set mostrano prima la lingua scelta, poi le carte presenti solo in inglese (segnate "dati EN"). Una carta trovata così si può registrare comunque come copia italiana.

## Altre funzioni

- Avanzamento dei set ("Hai 23 carte su 102") con le carte mancanti in trasparenza
- Immagine inglese usata come ripiego se manca nella lingua scelta
- Annullamento dell'eliminazione, unione automatica di copie identiche

## Limiti noti

- I prezzi sono le medie di mercato fornite da TCGdex, non i singoli "venduti" di eBay (l'API eBay per i venduti è ad accesso limitato e lo scraping è bloccato/vietato).
- I prezzi non distinguono la lingua: si compensa con la correzione per lingua.
- Il nome "Pokémon" è un marchio: prima di pubblicare su Google Play scegli un nome che non lo contenga e controlla le regole di Play sui contenuti con marchi di terzi.

## Compilare l'APK

Con GitHub Actions: carica il progetto in un repository, il workflow `.github/workflows/build-apk.yml` compila l'APK debug e lo pubblica come artifact ("Actions" → ultima esecuzione → `cartella-tcg-debug-apk`).

In locale (Android Studio, JDK 17): apri la cartella e premi Run, oppure `./gradlew assembleDebug`.

## Struttura

- `data/` – client TCGdex, valutazione per condizione, database Room, impostazioni (DataStore)
- `AppViewModel.kt` – stato e logica
- `ui/` – schermate Compose (Cerca, Collezione, Impostazioni, dettaglio carta)

## Prossimi passi possibili

- Scanner con la fotocamera per riconoscere la carta
- Venduti reali eBay (se si ottiene l'accesso alla Marketplace Insights API) o aggregatore a pagamento
- Server che aggiorna i prezzi una volta al giorno
- Export/import CSV della collezione
