// Client CardTrader lato server: un solo token (variabile CARDTRADER_TOKEN),
// cache in memoria e una coda che rispetta i limiti dell'API.
import { items, matchExpansion, findBlueprint, parseOffers, ctLanguage } from "./matching.js";

const BASE = "https://api.cardtrader.com/api/v2";
const HOUR = 60 * 60 * 1000;

export class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

/** Cache con scadenza; tiene anche gli esiti negativi (null) per non ripetere richieste inutili. */
class TtlCache {
  constructor(max = 20000) {
    this.map = new Map();
    this.max = max;
  }
  get(key) {
    const e = this.map.get(key);
    if (!e) return undefined;
    if (Date.now() > e.expires) {
      this.map.delete(key);
      return undefined;
    }
    return e;
  }
  set(key, value, ttlMs) {
    if (this.map.size >= this.max) this.map.delete(this.map.keys().next().value);
    const e = { value, expires: Date.now() + ttlMs, stored: Date.now() };
    this.map.set(key, e);
    return e;
  }
  get size() {
    return this.map.size;
  }
}

export class CardTrader {
  /**
   * @param {object} opts
   * @param {string} opts.token token API CardTrader
   * @param {Function} [opts.fetchFn] per i test
   * @param {number} [opts.minGapMs] distanza minima tra due richieste di offerte
   */
  constructor({ token, fetchFn = fetch, minGapMs = 1100, offersTtlMs = 12 * HOUR, catalogTtlMs = 7 * 24 * HOUR }) {
    this.token = token;
    this.fetchFn = fetchFn;
    this.minGapMs = minGapMs;
    this.offersTtlMs = offersTtlMs;
    this.catalogTtlMs = catalogTtlMs;
    this.catalog = new TtlCache(2000); // giochi, espansioni, blueprint
    this.offers = new TtlCache(50000); // offerte per blueprint+lingua
    this.inflight = new Map();
    this.queue = Promise.resolve();
    this.lastMarketCall = 0;
    this.stats = { ctCalls: 0, cacheHits: 0, errors: 0 };
  }

  get configured() {
    return Boolean(this.token);
  }

  async get(path, query = {}) {
    const url = new URL(`${BASE}/${path}`);
    for (const [k, v] of Object.entries(query)) url.searchParams.set(k, String(v));
    this.stats.ctCalls++;
    const res = await this.fetchFn(url, {
      headers: { Authorization: `Bearer ${this.token}`, Accept: "application/json" },
    });
    if (res.status === 401) throw new HttpError(502, "Token CardTrader del server non valido");
    if (res.status === 404) return null;
    if (res.status === 429) throw new HttpError(503, "CardTrader: troppe richieste, riprova tra poco");
    if (!res.ok) throw new HttpError(502, `CardTrader ha risposto ${res.status}`);
    return res.json();
  }

  /** Esegue una sola volta richieste identiche contemporanee. */
  async once(key, fn) {
    if (this.inflight.has(key)) return this.inflight.get(key);
    const p = fn().finally(() => this.inflight.delete(key));
    this.inflight.set(key, p);
    return p;
  }

  async cachedCatalog(key, fn) {
    const hit = this.catalog.get(key);
    if (hit) return hit.value;
    const value = await this.once(key, fn);
    this.catalog.set(key, value, this.catalogTtlMs);
    return value;
  }

  async pokemonGameId() {
    return this.cachedCatalog("game", async () => {
      const games = items(await this.get("games"));
      const g = games.find((x) => `${x.name} ${x.display_name}`.toLowerCase().includes("pok"));
      if (!g) throw new HttpError(502, "Pokémon non trovato tra i giochi di CardTrader");
      return g.id;
    });
  }

  async expansions() {
    const gameId = await this.pokemonGameId();
    return this.cachedCatalog("expansions", async () =>
      items(await this.get("expansions")).filter((e) => e.game_id === gameId),
    );
  }

  async blueprints(expansionId) {
    return this.cachedCatalog(`bp:${expansionId}`, async () =>
      items(await this.get("blueprints/export", { expansion_id: expansionId })),
    );
  }

  /** Coda: le richieste di offerte partono una alla volta, distanziate di minGapMs. */
  schedule(fn) {
    const run = this.queue.then(async () => {
      const wait = this.lastMarketCall + this.minGapMs - Date.now();
      if (wait > 0) await new Promise((r) => setTimeout(r, wait));
      this.lastMarketCall = Date.now();
      return fn();
    });
    this.queue = run.catch(() => {});
    return run;
  }

  /**
   * Offerte di una carta in una lingua.
   * @returns {{blueprintId: number|null, offers: object[], fetchedAt: number|null, cached: boolean}}
   */
  async offersFor({ lang, setName, number, name, cardmarketIds = [], cardtraderId = null }) {
    let blueprintId = cardtraderId ? Number(cardtraderId) : null;
    if (!blueprintId) {
      const exp = matchExpansion(await this.expansions(), setName);
      if (exp) {
        const bp = findBlueprint(await this.blueprints(exp.id), { cardmarketIds, number, name });
        blueprintId = bp ? bp.id : null;
      }
    }
    if (!blueprintId) return { blueprintId: null, offers: [], fetchedAt: null, cached: false };

    const language = ctLanguage(lang);
    const key = `${blueprintId}:${language}`;
    const hit = this.offers.get(key);
    if (hit) {
      this.stats.cacheHits++;
      return { blueprintId, offers: hit.value, fetchedAt: hit.stored, cached: true };
    }
    const offers = await this.once(`offers:${key}`, () =>
      this.schedule(async () =>
        parseOffers(await this.get("marketplace/products", { blueprint_id: blueprintId, language })),
      ),
    );
    const e = this.offers.set(key, offers, this.offersTtlMs);
    return { blueprintId, offers, fetchedAt: e.stored, cached: false };
  }
}
