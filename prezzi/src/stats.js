// Algoritmo di stima (stessa logica di PriceEngine.kt nell'app). Funzioni pure, testate.

/** Coefficienti di condizione per riportare un'offerta a Near Mint. */
const CONDITION_FACTOR = {
  "mint": 1.05,
  "near mint": 1.0,
  "slightly played": 0.85,
  "moderately played": 0.7,
  "played": 0.4,
  "heavily played": 0.25,
  "poor": 0.25,
};

export function conditionFactor(name) {
  return CONDITION_FACTOR[String(name || "").trim().toLowerCase()] ?? 1.0;
}

export function median(values) {
  if (values.length === 0) throw new Error("median di un elenco vuoto");
  const s = [...values].sort((a, b) => a - b);
  const m = Math.floor(s.length / 2);
  return s.length % 2 === 1 ? s[m] : (s[m - 1] + s[m]) / 2;
}

/** Varianti: n = normale/holo, r = reverse, f = 1ª edizione. */
export function matchesVariant(o, v) {
  if (v === "r") return o.reverse === true;
  if (v === "f") return o.firstEdition === true;
  return o.reverse !== true && o.firstEdition !== true;
}

/**
 * Stima Near Mint dalle offerte di una lingua:
 * esclude graded/lotti/altre valute, riporta a NM, scarta un'offerta anomala
 * (meno della metà della successiva) e prende la mediana delle 5 più economiche.
 * @returns {[number, number] | null} [prezzo NM, numero di offerte]
 */
export function estimate(offers, variant, currency = "EUR") {
  const usable = offers.filter(
    (o) => !o.graded && o.bundle <= 1 && o.quantity > 0 && o.price > 0 && o.currency === currency && matchesVariant(o, variant),
  );
  if (usable.length === 0) return null;
  const nm = usable.map((o) => o.price / conditionFactor(o.condition)).sort((a, b) => a - b);
  if (nm.length >= 4 && nm[0] < 0.5 * nm[1]) nm.shift();
  return [round2(median(nm.slice(0, 5))), usable.length];
}

export const MIN_OFFERS_FOR_COEF = 3;
export const MIN_SAMPLES_FOR_COEF = 5;

/**
 * Rapporto lingua / inglese di una carta, entrambi su CardTrader (così il sovrapprezzo
 * del sito si annulla). Solo se entrambe le lingue hanno abbastanza offerte.
 */
export function cardRatio(langEntry, enEntry, variant = "n") {
  const a = langEntry?.[variant];
  const b = enEntry?.[variant];
  if (!a || !b || a[1] < MIN_OFFERS_FOR_COEF || b[1] < MIN_OFFERS_FOR_COEF || !(b[0] > 0)) return null;
  return a[0] / b[0];
}

/**
 * Coefficienti di lingua: mediana dei rapporti lingua/inglese, per espansione e in totale.
 * @param samples [{exp, ratio}]
 * @returns {{all?: [number, number], [exp]: [number, number]}}
 */
export function coefficients(samples) {
  const out = {};
  const byExp = new Map();
  for (const s of samples) {
    if (!(s.ratio > 0)) continue;
    const r = Math.min(5, Math.max(0.1, s.ratio));
    if (!byExp.has(s.exp)) byExp.set(s.exp, []);
    byExp.get(s.exp).push(r);
  }
  const all = [];
  for (const [exp, list] of byExp) {
    all.push(...list);
    if (list.length >= MIN_SAMPLES_FOR_COEF) out[exp] = [round3(median(list)), list.length];
  }
  if (all.length >= MIN_SAMPLES_FOR_COEF) out.all = [round3(median(all)), all.length];
  return out;
}

export const round2 = (x) => Math.round(x * 100) / 100;
export const round3 = (x) => Math.round(x * 1000) / 1000;
