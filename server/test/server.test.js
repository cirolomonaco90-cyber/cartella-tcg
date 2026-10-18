import test from "node:test";
import assert from "node:assert/strict";
import { CardTrader } from "../src/cardtrader.js";
import { createServer } from "../src/index.js";

/** Finto CardTrader: risponde come l'API vera e conta le chiamate. */
function fakeFetch(calls) {
  return async (url) => {
    const u = new URL(url);
    calls.push(u.pathname + u.search);
    const json = (body, status = 200) => ({ ok: status < 400, status, json: async () => body });
    if (u.pathname.endsWith("/games")) return json({ array: [{ id: 1, name: "Magic" }, { id: 6, name: "Pokemon", display_name: "Pokémon" }] });
    if (u.pathname.endsWith("/expansions")) return json([{ id: 100, game_id: 6, name: "Aquapolis" }, { id: 200, game_id: 1, name: "Aquapolis" }]);
    if (u.pathname.endsWith("/blueprints/export")) {
      assert.equal(u.searchParams.get("expansion_id"), "100");
      return json([{ id: 555, name: "Lugia", card_market_ids: [9999], fixed_properties: { collector_number: "149/147" } }]);
    }
    if (u.pathname.endsWith("/marketplace/products")) {
      assert.equal(u.searchParams.get("blueprint_id"), "555");
      const lang = u.searchParams.get("language");
      const cents = lang === "it" ? 4000 : 9000;
      return json({ "555": [{ price: { cents, currency: "EUR" }, quantity: 1, properties_hash: { condition: "Near Mint" } }] });
    }
    return json({}, 404);
  };
}

async function withServer(ct, appKey, fn) {
  const server = createServer({ cardTrader: ct, appKey });
  await new Promise((r) => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    await fn(base);
  } finally {
    server.close();
  }
}

test("offerte per lingua, con cache", async () => {
  const calls = [];
  const ct = new CardTrader({ token: "t", fetchFn: fakeFetch(calls), minGapMs: 0 });
  await withServer(ct, "k", async (base) => {
    const q = "lang=it&setName=Aquapolis&number=149&name=Lugia&cm=9999";
    let r = await fetch(`${base}/v1/offers?${q}`, { headers: { "X-App-Key": "k" } });
    assert.equal(r.status, 200);
    let body = await r.json();
    assert.equal(body.blueprintId, 555);
    assert.equal(body.offers[0].price, 40);
    assert.equal(body.cached, false);

    // Seconda richiesta uguale: dalla cache, nessuna nuova chiamata a CardTrader.
    const before = calls.length;
    body = await (await fetch(`${base}/v1/offers?${q}`, { headers: { "X-App-Key": "k" } })).json();
    assert.equal(body.cached, true);
    assert.equal(calls.length, before);

    // Lingua diversa: prezzo diverso, catalogo già in cache (una sola chiamata).
    body = await (await fetch(`${base}/v1/offers?lang=en&setName=Aquapolis&cm=9999`, { headers: { "X-App-Key": "k" } })).json();
    assert.equal(body.offers[0].price, 90);
    assert.equal(calls.length, before + 1);
  });
});

test("carta non trovata", async () => {
  const ct = new CardTrader({ token: "t", fetchFn: fakeFetch([]), minGapMs: 0 });
  await withServer(ct, "", async (base) => {
    const body = await (await fetch(`${base}/v1/offers?lang=it&setName=Set Inesistente`)).json();
    assert.equal(body.blueprintId, null);
    assert.deepEqual(body.offers, []);
  });
});

test("chiave app, token mancante e parametri", async () => {
  const ct = new CardTrader({ token: "", fetchFn: fakeFetch([]) });
  await withServer(ct, "k", async (base) => {
    assert.equal((await fetch(`${base}/v1/offers?lang=it`)).status, 401);
    assert.equal((await fetch(`${base}/v1/offers?lang=it`, { headers: { "X-App-Key": "k" } })).status, 503);
    const health = await (await fetch(`${base}/health`)).json();
    assert.equal(health.cardtrader, false);
  });
  const ok = new CardTrader({ token: "t", fetchFn: fakeFetch([]) });
  await withServer(ok, "", async (base) => {
    assert.equal((await fetch(`${base}/v1/offers`)).status, 400);
  });
});

test("token rifiutato da CardTrader", async () => {
  const ct = new CardTrader({ token: "bad", fetchFn: async () => ({ ok: false, status: 401, json: async () => ({}) }) });
  await withServer(ct, "", async (base) => {
    const r = await fetch(`${base}/v1/offers?lang=it&setName=Aquapolis`);
    assert.equal(r.status, 502);
    assert.match((await r.json()).error, /Token/);
  });
});

test("la coda distanzia le richieste di offerte", async () => {
  const ct = new CardTrader({ token: "t", fetchFn: fakeFetch([]), minGapMs: 150 });
  const start = Date.now();
  await Promise.all([
    ct.offersFor({ lang: "it", setName: "Aquapolis", cardmarketIds: [9999] }),
    ct.offersFor({ lang: "en", setName: "Aquapolis", cardmarketIds: [9999] }),
    ct.offersFor({ lang: "fr", setName: "Aquapolis", cardmarketIds: [9999] }),
  ]);
  assert.ok(Date.now() - start >= 280, "tre richieste devono richiedere almeno 2 intervalli");
});
