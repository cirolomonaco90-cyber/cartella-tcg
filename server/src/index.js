// Server prezzi di Cartella TCG.
//
// GET /health                 -> stato del servizio
// GET /v1/offers?lang=it&setName=Aquapolis&number=149&name=Lugia&cm=123,456&ct=789
//                             -> { blueprintId, offers: [...], fetchedAt, cached }
//
// Variabili d'ambiente:
//   CARDTRADER_TOKEN  token API CardTrader (obbligatorio per avere le offerte)
//   APP_KEY           chiave condivisa con l'app (header X-App-Key), contro gli abusi
//   PORT              porta (Railway la imposta da solo)
import http from "node:http";
import { CardTrader, HttpError } from "./cardtrader.js";

const LIMIT_PER_MIN = Number(process.env.RATE_LIMIT_PER_MIN || 120);

export function createServer({ cardTrader, appKey }) {
  const hits = new Map(); // ip -> { count, windowStart }

  function rateLimited(ip) {
    const now = Date.now();
    const h = hits.get(ip);
    if (!h || now - h.windowStart > 60_000) {
      hits.set(ip, { count: 1, windowStart: now });
      if (hits.size > 10_000) hits.clear();
      return false;
    }
    h.count++;
    return h.count > LIMIT_PER_MIN;
  }

  function send(res, status, body) {
    res.writeHead(status, { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" });
    res.end(JSON.stringify(body));
  }

  return http.createServer(async (req, res) => {
    const url = new URL(req.url, "http://localhost");
    try {
      if (req.method !== "GET") return send(res, 405, { error: "Solo GET" });

      if (url.pathname === "/health" || url.pathname === "/") {
        return send(res, 200, {
          ok: true,
          cardtrader: cardTrader.configured,
          cachedOffers: cardTrader.offers.size,
          stats: cardTrader.stats,
        });
      }

      if (url.pathname === "/v1/offers") {
        if (appKey && req.headers["x-app-key"] !== appKey) return send(res, 401, { error: "Chiave app mancante" });
        const ip = (req.headers["x-forwarded-for"] || req.socket.remoteAddress || "").split(",")[0].trim();
        if (rateLimited(ip)) return send(res, 429, { error: "Troppe richieste" });
        if (!cardTrader.configured) return send(res, 503, { error: "CardTrader non configurato sul server" });

        const q = url.searchParams;
        const lang = q.get("lang");
        if (!lang) return send(res, 400, { error: "Parametro lang obbligatorio" });
        const result = await cardTrader.offersFor({
          lang,
          setName: q.get("setName") || "",
          number: q.get("number") || "",
          name: q.get("name") || "",
          cardmarketIds: (q.get("cm") || "").split(",").filter(Boolean),
          cardtraderId: q.get("ct") || null,
        });
        return send(res, 200, result);
      }

      return send(res, 404, { error: "Non trovato" });
    } catch (e) {
      cardTrader.stats.errors++;
      const status = e instanceof HttpError ? e.status : 500;
      if (status === 500) console.error(e);
      return send(res, status, { error: e.message || "Errore interno" });
    }
  });
}

// Avvio solo se eseguito direttamente (non nei test).
if (import.meta.url === `file://${process.argv[1]}`) {
  const cardTrader = new CardTrader({ token: process.env.CARDTRADER_TOKEN || "" });
  const port = Number(process.env.PORT || 3000);
  createServer({ cardTrader, appKey: process.env.APP_KEY || "" }).listen(port, () => {
    console.log(`Server prezzi in ascolto sulla porta ${port} (CardTrader ${cardTrader.configured ? "configurato" : "NON configurato"})`);
  });
}
