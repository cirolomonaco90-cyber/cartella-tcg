// Job notturno: costruisce i file dei prezzi per lingua letti dall'app.
//
//   CARDTRADER_TOKEN=... node src/build.js <cartella-output>
//
// Output (cartella-output/v1/):
//   catalog.json  { v, updated, exp: {expId: nome}, cm: {idProdottoCardmarket: expId} }
//   <lingua>.json { v, lang, updated, coef: {all: [r, n], expId: [r, n]},
//                   p: {idProdottoCardmarket: {n: [prezzoNM, offerte], r: [...], f: [...]}} }
//     n = normale/holo, r = reverse, f = 1ª edizione; prezzi NM in euro.
//     coef = mediana di (prezzo lingua / trend Cardmarket), per espansione e in totale.
//
// Variabili opzionali: LANGS (default en,it,fr,de,es,pt), MAX_EXPANSIONS (per prove).
import fs from "node:fs/promises";
import path from "node:path";
import { CardTrader } from "./cardtrader.js";
import { estimate, coefficients, MIN_OFFERS_FOR_COEF } from "./stats.js";

export const DEFAULT_LANGS = ["en", "it", "fr", "de", "es", "pt"];
const CARDMARKET_GUIDE = "https://downloads.s3.cardmarket.com/productCatalog/priceGuide/price_guide_6.json";

/** Trend Cardmarket per idProduct (stessa fonte pubblica usata da TCGdex). */
export async function loadCardmarketTrends(fetchFn = fetch) {
  const res = await fetchFn(CARDMARKET_GUIDE);
  if (!res.ok) throw new Error(`Cardmarket price guide: HTTP ${res.status}`);
  const data = await res.json();
  const map = new Map();
  for (const g of data.priceGuides || []) {
    const t = g.trend ?? g.avg30 ?? g.avg;
    if (typeof g.idProduct === "number" && t > 0) map.set(g.idProduct, t);
  }
  return map;
}

/**
 * Costruisce catalogo e file per lingua.
 * @returns {{catalog: object, perLang: Record<string, object>, log: string[]}}
 */
export async function build({ ct, cmTrends = new Map(), langs = DEFAULT_LANGS, maxExpansions = Infinity, now = new Date() }) {
  const log = [];
  const updated = now.toISOString();
  const gameId = await ct.pokemonGameId();
  const expansions = (await ct.expansions(gameId)).slice(0, maxExpansions);
  log.push(`Espansioni Pokémon su CardTrader: ${expansions.length}`);

  // 1) Catalogo: quali prodotti Cardmarket corrispondono a quali blueprint ed espansioni.
  const catalog = { v: 1, updated, exp: {}, cm: {} };
  const bpToCm = new Map(); // blueprintId -> [idProduct]
  const useful = [];
  for (const e of expansions) {
    let bps;
    try {
      bps = await ct.blueprints(e.id);
    } catch (err) {
      log.push(`! blueprint ${e.name}: ${err.message}`);
      continue;
    }
    let any = false;
    for (const bp of bps) {
      const ids = (bp.card_market_ids || []).map(Number).filter(Number.isFinite);
      if (ids.length === 0) continue;
      bpToCm.set(bp.id, ids);
      for (const id of ids) catalog.cm[id] = e.id;
      any = true;
    }
    if (any) {
      catalog.exp[e.id] = e.name;
      useful.push(e);
    }
  }
  log.push(`Espansioni con carte collegate a Cardmarket: ${useful.length}, prodotti: ${Object.keys(catalog.cm).length}`);

  // 2) Offerte per lingua, un'espansione alla volta.
  const perLang = {};
  for (const lang of langs) {
    const p = {};
    const samples = [];
    let failures = 0;
    for (const e of useful) {
      let byBp;
      try {
        byBp = await ct.offersByExpansion(e.id, lang);
      } catch (err) {
        failures++;
        if (/Token/.test(err.message)) throw err;
        continue;
      }
      for (const [bpId, offers] of byBp) {
        const ids = bpToCm.get(bpId);
        if (!ids) continue;
        const entry = {};
        for (const v of ["n", "r", "f"]) {
          const est = estimate(offers, v);
          if (est) entry[v] = est;
        }
        if (Object.keys(entry).length === 0) continue;
        for (const id of ids) p[id] = entry;
        // Campione per il coefficiente: versione normale con abbastanza offerte.
        const trend = cmTrends.get(ids[0]);
        if (entry.n && entry.n[1] >= MIN_OFFERS_FOR_COEF && trend > 0) {
          samples.push({ exp: e.id, ratio: entry.n[0] / trend });
        }
      }
    }
    const coef = coefficients(samples);
    perLang[lang] = { v: 1, lang, updated, coef, p };
    log.push(
      `${lang}: ${Object.keys(p).length} prodotti con offerte, coefficiente totale ` +
        `${coef.all ? `${Math.round(coef.all[0] * 100)}% su ${coef.all[1]} carte` : "n/d"}` +
        (failures ? `, ${failures} espansioni non lette` : ""),
    );
  }
  return { catalog, perLang, log };
}

async function main() {
  const out = process.argv[2] || "out";
  const token = process.env.CARDTRADER_TOKEN;
  if (!token) {
    console.log("CARDTRADER_TOKEN non impostato: niente da fare.");
    process.exit(0);
  }
  const langs = (process.env.LANGS || DEFAULT_LANGS.join(",")).split(",").map((s) => s.trim()).filter(Boolean);
  const maxExpansions = Number(process.env.MAX_EXPANSIONS || Infinity);

  let cmTrends = new Map();
  try {
    cmTrends = await loadCardmarketTrends();
    console.log(`Trend Cardmarket caricati: ${cmTrends.size}`);
  } catch (e) {
    console.log(`Attenzione, coefficienti non calcolabili: ${e.message}`);
  }

  const ct = new CardTrader({ token });
  const started = Date.now();
  const { catalog, perLang, log } = await build({ ct, cmTrends, langs, maxExpansions });
  log.forEach((l) => console.log(l));

  const dir = path.join(out, "v1");
  await fs.mkdir(dir, { recursive: true });
  await fs.writeFile(path.join(dir, "catalog.json"), JSON.stringify(catalog));
  for (const [lang, data] of Object.entries(perLang)) {
    await fs.writeFile(path.join(dir, `${lang}.json`), JSON.stringify(data));
  }
  console.log(`Fatto in ${Math.round((Date.now() - started) / 60000)} minuti, ${ct.calls} chiamate a CardTrader.`);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((e) => {
    console.error(e);
    process.exit(1);
  });
}
