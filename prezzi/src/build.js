// Job notturno: costruisce i file dei prezzi per lingua letti dall'app.
//
//   CARDTRADER_TOKEN=... node src/build.js <cartella-output>
//
// Output (cartella-output/v1/):
//   catalog.json  { v, updated, exp: {expId: nome}, cm: {idProdottoCardmarket: expId} }
//   <lingua>.json { v, lang, updated, coef: {all: [r, n], expId: [r, n]},
//                   p: {idProdottoCardmarket: {n: [prezzoNM, offerte], r: [...], f: [...]}} }
//     n = normale/holo, r = reverse, f = 1ª edizione; prezzi NM in euro (offerte CardTrader).
//     coef = mediana del rapporto prezzo lingua / prezzo inglese, entrambi su CardTrader
//            (così il sovrapprezzo del sito si annulla), per espansione e in totale.
//            L'app moltiplica il prezzo Cardmarket per questo rapporto.
//
// Variabili opzionali: LANGS (default en,it,fr,de,es,pt; l'inglese serve sempre come riferimento),
// MAX_EXPANSIONS (per prove).
import fs from "node:fs/promises";
import path from "node:path";
import { CardTrader } from "./cardtrader.js";
import { estimate, coefficients, cardRatio } from "./stats.js";

export const DEFAULT_LANGS = ["en", "it", "fr", "de", "es", "pt"];

/**
 * Costruisce catalogo e file per lingua.
 * @returns {{catalog: object, perLang: Record<string, object>, log: string[]}}
 */
export async function build({ ct, langs = DEFAULT_LANGS, maxExpansions = Infinity, now = new Date() }) {
  const log = [];
  const updated = now.toISOString();
  // L'inglese è il riferimento per i rapporti: va sempre calcolato, e per primo.
  const order = ["en", ...langs.filter((l) => l !== "en")];
  const gameId = await ct.pokemonGameId();
  const expansions = (await ct.expansions(gameId)).slice(0, maxExpansions);
  log.push(`Espansioni Pokémon su CardTrader: ${expansions.length}`);

  // 1) Catalogo: quali prodotti Cardmarket corrispondono a quali blueprint ed espansioni.
  const all = []; // [{exp, bps}]
  for (const e of expansions) {
    try {
      all.push({ exp: e, bps: await ct.blueprints(e.id) });
    } catch (err) {
      log.push(`! blueprint ${e.name}: ${err.message}`);
    }
  }
  // Solo carte singole: la categoria più frequente tra i blueprint collegati a Cardmarket.
  // Le altre (box, buste, accessori) falserebbero prezzi e coefficienti.
  const categoryCount = new Map();
  for (const { bps } of all) {
    for (const bp of bps) {
      if ((bp.card_market_ids || []).length === 0) continue;
      categoryCount.set(bp.category_id, (categoryCount.get(bp.category_id) || 0) + 1);
    }
  }
  const singlesCategory = [...categoryCount.entries()].sort((a, b) => b[1] - a[1])[0]?.[0];
  log.push(`Categorie dei blueprint (id: quantità): ${[...categoryCount.entries()].map(([k, v]) => `${k}: ${v}`).join(", ")}; carte singole = ${singlesCategory}`);

  const catalog = { v: 1, updated, exp: {}, cm: {} };
  const bpToCm = new Map(); // blueprintId -> [idProduct]
  const useful = [];
  for (const { exp: e, bps } of all) {
    let any = false;
    for (const bp of bps) {
      if (singlesCategory !== undefined && bp.category_id !== singlesCategory) continue;
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
  const enByBp = new Map(); // blueprintId -> voce inglese (riferimento)
  for (const lang of order) {
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
        if (lang === "en") {
          enByBp.set(bpId, entry);
        } else {
          const r = cardRatio(entry, enByBp.get(bpId), "n");
          if (r !== null) samples.push({ exp: e.id, ratio: r });
        }
      }
    }
    const coef = lang === "en" ? {} : coefficients(samples);
    perLang[lang] = { v: 1, lang, updated, coef, p };
    log.push(
      `${lang}: ${Object.keys(p).length} prodotti con offerte` +
        (lang === "en"
          ? " (riferimento)"
          : `, rapporto con l'inglese ${coef.all ? `${Math.round(coef.all[0] * 100)}% su ${coef.all[1]} carte` : "n/d"}`) +
        (failures ? `, ${failures} espansioni non lette` : ""),
    );
  }
  // Solo le lingue richieste finiscono nei file.
  for (const l of Object.keys(perLang)) if (!langs.includes(l)) delete perLang[l];
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

  const ct = new CardTrader({ token });
  const started = Date.now();
  const { catalog, perLang, log } = await build({ ct, langs, maxExpansions });
  log.forEach((l) => console.log(l));

  const dir = path.join(out, "v1");
  await fs.mkdir(dir, { recursive: true });
  await fs.writeFile(path.join(dir, "catalog.json"), JSON.stringify(catalog));
  for (const [lang, data] of Object.entries(perLang)) {
    await fs.writeFile(path.join(dir, `${lang}.json`), JSON.stringify(data));
  }
  const done = `Fatto in ${Math.round((Date.now() - started) / 60000)} minuti, ${ct.calls} chiamate a CardTrader.`;
  console.log(done);
  // Registro leggibile pubblicato con i dati (i log di Actions non sempre sono consultabili).
  await fs.writeFile(path.join(out, "log.txt"), [...log, done].join("\n") + "\n");
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((e) => {
    console.error(e);
    process.exit(1);
  });
}
