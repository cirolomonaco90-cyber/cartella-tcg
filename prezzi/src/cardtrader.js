// Client CardTrader per il job notturno: un solo token (secret del repo),
// richieste in coda rispettando i limiti dell'API, nuovi tentativi in caso di 429.
import { items, parseOffersByBlueprint, ctLanguage } from "./matching.js";

const BASE = "https://api.cardtrader.com/api/v2";
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export class CardTrader {
  /**
   * @param {object} opts
   * @param {string} opts.token
   * @param {Function} [opts.fetchFn] per i test
   * @param {number} [opts.marketGapMs] distanza tra richieste di offerte (limite ~1/s)
   * @param {number} [opts.catalogGapMs] distanza tra richieste di catalogo (limite 200/10s)
   */
  constructor({ token, fetchFn = fetch, marketGapMs = 1100, catalogGapMs = 80, retryWaitMs = 15000 }) {
    this.token = token;
    this.fetchFn = fetchFn;
    this.marketGapMs = marketGapMs;
    this.catalogGapMs = catalogGapMs;
    this.retryWaitMs = retryWaitMs;
    this.calls = 0;
  }

  async get(path, query = {}, attempt = 1) {
    const url = new URL(`${BASE}/${path}`);
    for (const [k, v] of Object.entries(query)) url.searchParams.set(k, String(v));
    this.calls++;
    const res = await this.fetchFn(url, {
      headers: { Authorization: `Bearer ${this.token}`, Accept: "application/json" },
    });
    if (res.status === 401) throw new Error("Token CardTrader non valido (401)");
    if (res.status === 404) return null;
    if ((res.status === 429 || res.status >= 500) && attempt < 4) {
      await sleep(this.retryWaitMs * attempt);
      return this.get(path, query, attempt + 1);
    }
    if (!res.ok) throw new Error(`CardTrader ${path}: HTTP ${res.status}`);
    return res.json();
  }

  async pokemonGameId() {
    const games = items(await this.get("games"));
    const g = games.find((x) => `${x.name} ${x.display_name}`.toLowerCase().includes("pok"));
    if (!g) throw new Error("Pokémon non trovato tra i giochi di CardTrader");
    return g.id;
  }

  async expansions(gameId) {
    return items(await this.get("expansions")).filter((e) => e.game_id === gameId);
  }

  async blueprints(expansionId) {
    await sleep(this.catalogGapMs);
    return items(await this.get("blueprints/export", { expansion_id: expansionId }));
  }

  /** Le offerte più economiche (max 25 per carta) di un'intera espansione, in una lingua. */
  async offersByExpansion(expansionId, lang) {
    await sleep(this.marketGapMs);
    const json = await this.get("marketplace/products", { expansion_id: expansionId, language: ctLanguage(lang) });
    return parseOffersByBlueprint(json);
  }
}
