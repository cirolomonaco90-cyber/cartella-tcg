import test from "node:test";
import assert from "node:assert/strict";
import { build } from "../src/build.js";
import { CardTrader } from "../src/cardtrader.js";
import { estimate, coefficients, cardRatio } from "../src/stats.js";

const offer = (price, extra = {}) => ({
  price, currency: "EUR", condition: "Near Mint", reverse: null, firstEdition: null, graded: false, bundle: 1, quantity: 1, ...extra,
});

test("stima: mediana delle 5 più economiche riportate a NM", () => {
  assert.deepEqual(estimate([10, 12, 11, 30, 13, 14, 50].map((p) => offer(p)), "n"), [12, 7]);
  assert.deepEqual(estimate([offer(7, { condition: "Played" })], "n"), [17.5, 1]);
  // anomalo scartato, graded e lotti esclusi
  assert.deepEqual(estimate([offer(1), offer(10), offer(11), offer(12), offer(2, { graded: true }), offer(3, { bundle: 4 })], "n"), [11, 4]);
  // varianti
  const mix = [offer(5), offer(20, { reverse: true }), offer(200, { firstEdition: true })];
  assert.deepEqual(estimate(mix, "r"), [20, 1]);
  assert.deepEqual(estimate(mix, "f"), [200, 1]);
  assert.deepEqual(estimate(mix, "n"), [5, 1]);
  assert.equal(estimate([offer(5, { currency: "USD" })], "n"), null);
});

test("rapporto lingua/inglese sulla stessa carta", () => {
  assert.equal(cardRatio({ n: [72, 3] }, { n: [102, 4] }), 72 / 102);
  assert.equal(cardRatio({ n: [72, 2] }, { n: [102, 4] }), null); // troppe poche offerte
  assert.equal(cardRatio({ n: [72, 3] }, undefined), null);
  assert.equal(cardRatio({ r: [10, 5] }, { r: [20, 5] }, "r"), 0.5);
});

test("coefficienti per espansione e totale", () => {
  const s = [0.6, 0.7, 0.8, 0.7, 0.9].map((ratio) => ({ exp: 1, ratio }));
  s.push({ exp: 2, ratio: 1.2 });
  const c = coefficients(s);
  assert.deepEqual(c[1], [0.7, 5]);
  assert.equal(c[2], undefined); // troppo pochi campioni
  assert.deepEqual(c.all, [0.75, 6]);
});

/** Finto CardTrader con due espansioni e prezzi diversi per lingua. */
function fakeFetch(calls) {
  return async (url) => {
    const u = new URL(url);
    calls.push(u.pathname + u.search);
    const json = (body, status = 200) => ({ ok: status < 400, status, json: async () => body });
    const p = u.pathname;
    if (p.endsWith("/games")) return json([{ id: 6, name: "Pokemon" }]);
    if (p.endsWith("/expansions")) return json([
      { id: 100, game_id: 6, name: "Aquapolis" },
      { id: 101, game_id: 6, name: "Senza Cardmarket" },
      { id: 200, game_id: 1, name: "Magic" },
    ]);
    if (p.endsWith("/blueprints/export")) {
      const exp = u.searchParams.get("expansion_id");
      if (exp === "100") return json([
        { id: 1, name: "Lugia", category_id: 73, card_market_ids: [9001] },
        { id: 2, name: "Kingdra", category_id: 73, card_market_ids: [9002, 9003] },
        { id: 4, name: "Booster Box", category_id: 80, card_market_ids: [9100] }, // sigillato: escluso
      ]);
      return json([{ id: 3, name: "Promo", card_market_ids: [] }]);
    }
    if (p.endsWith("/marketplace/products")) {
      assert.equal(u.searchParams.get("expansion_id"), "100"); // solo espansioni utili
      const it = u.searchParams.get("language") === "it";
      const mk = (bp, cents, props = {}) => ({ blueprint_id: bp, price: { cents, currency: "EUR" }, quantity: 1, properties_hash: { condition: "Near Mint", ...props } });
      return json({
        "1": [mk(1, it ? 7000 : 10000), mk(1, it ? 7200 : 10200), mk(1, it ? 7400 : 10400), mk(1, 30000, { pokemon_reverse: true })],
        "2": [mk(2, it ? 300 : 500)],
      });
    }
    return json({}, 404);
  };
}

test("job completo con CardTrader simulato", async () => {
  const calls = [];
  const ct = new CardTrader({ token: "t", fetchFn: fakeFetch(calls), marketGapMs: 0, catalogGapMs: 0 });
  const { catalog, perLang } = await build({ ct, langs: ["it"], now: new Date("2026-10-18T00:00:00Z") });

  assert.deepEqual(catalog.exp, { 100: "Aquapolis" });
  assert.deepEqual(catalog.cm, { 9001: 100, 9002: 100, 9003: 100 });

  const it = perLang.it;
  assert.deepEqual(it.p[9001].n, [72, 3]);
  assert.deepEqual(it.p[9001].r, [300, 1]);
  assert.deepEqual(it.p[9003].n, [3, 1]); // stessi dati per tutti gli id Cardmarket del blueprint
  // L'inglese è calcolato comunque come riferimento, ma non pubblicato se non richiesto.
  assert.equal(perLang.en, undefined);
  // Solo Lugia ha >= 3 offerte in entrambe le lingue: 1 campione, sotto il minimo per un coefficiente.
  assert.deepEqual(it.coef, {});
  // 2 lingue (en di riferimento + it) x 1 espansione utile = 2 richieste di offerte
  assert.equal(calls.filter((c) => c.includes("marketplace")).length, 2);
});

test("un token sbagliato ferma il job", async () => {
  const ct = new CardTrader({ token: "x", fetchFn: async () => ({ ok: false, status: 401, json: async () => ({}) }) });
  await assert.rejects(build({ ct }), /Token/);
});
