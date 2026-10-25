// Relais Camara AI (Cloudflare Worker).
// Il garde la clé Anthropic cachée : l'application envoie un code d'accès, le relais vérifie le code,
// ajoute la clé et transmet la demande à Claude. Aucune conversation n'est enregistrée ici.
//
// Variables à créer dans Cloudflare (Settings > Variables and Secrets) :
//   ANTHROPIC_KEY   (secret)  la clé API Anthropic du propriétaire
//   ACCESS_CODES    (secret)  les codes d'accès séparés par des virgules, ex. : abc123xyz,def456uvw
//   DAILY_LIMIT     (texte, facultatif)  nombre maximal de requêtes par code et par jour, 200 par défaut
//   ALLOWED_MODELS  (texte, facultatif)  modèles permis, séparés par des virgules
// Liaison facultative : un espace KV nommé LIMITS (active la limite journalière par code).

const ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";
const MAX_BODY = 300000;
const DEFAULT_MODELS = "claude-haiku-5-5,claude-sonnet-5-5";

function fail(status, message) {
  return new Response(
    JSON.stringify({ type: "error", error: { type: "relay_error", message } }),
    { status, headers: { "content-type": "application/json" } }
  );
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/") {
      return new Response("Relais Camara AI en ligne.", { status: 200 });
    }
    if (request.method !== "POST" || url.pathname !== "/v1/messages") {
      return fail(404, "Adresse inconnue.");
    }
    if (!env.ANTHROPIC_KEY || !env.ACCESS_CODES) {
      return fail(500, "Le relais n'est pas encore configuré.");
    }

    const code = (request.headers.get("x-camara-code") || "").trim();
    const codes = env.ACCESS_CODES.split(",").map((s) => s.trim()).filter(Boolean);
    if (!code || !codes.includes(code)) {
      return fail(401, "Code d'accès refusé.");
    }

    const raw = await request.text();
    if (raw.length > MAX_BODY) return fail(413, "Message trop long.");
    let body;
    try {
      body = JSON.parse(raw);
    } catch (e) {
      return fail(400, "Requête invalide.");
    }

    // Limites de sécurité : modèles permis, longueur de réponse, outils connus seulement.
    const allowed = (env.ALLOWED_MODELS || DEFAULT_MODELS).split(",").map((s) => s.trim()).filter(Boolean);
    if (!allowed.includes(body.model)) body.model = allowed[0];
    body.max_tokens = Math.min(Number(body.max_tokens) || 1000, 1500);
    delete body.stream;
    if (Array.isArray(body.tools)) {
      body.tools = body.tools
        .filter((t) => !t.type || t.type === "custom" || t.type === "web_search_20250305")
        .map((t) => (t.type === "web_search_20250305" ? { ...t, max_uses: Math.min(Number(t.max_uses) || 3, 5) } : t));
    }

    // Limite journalière par code (si l'espace KV « LIMITS » est relié).
    if (env.LIMITS) {
      const day = new Date().toISOString().slice(0, 10);
      const counter = code + ":" + day;
      const used = parseInt((await env.LIMITS.get(counter)) || "0", 10);
      const max = parseInt(env.DAILY_LIMIT || "200", 10);
      if (used >= max) {
        return fail(429, "Limite du jour atteinte pour ton code. Réessaie demain.");
      }
      await env.LIMITS.put(counter, String(used + 1), { expirationTtl: 172800 });
    }

    const upstream = await fetch(ANTHROPIC_URL, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-api-key": env.ANTHROPIC_KEY,
        "anthropic-version": "2023-06-01",
      },
      body: JSON.stringify(body),
    });

    if (upstream.status === 401 || upstream.status === 403) {
      return fail(502, "Le propriétaire doit vérifier la clé du relais.");
    }
    return new Response(upstream.body, {
      status: upstream.status,
      headers: { "content-type": upstream.headers.get("content-type") || "application/json" },
    });
  },
};
