// MineLauncher — CurseForge API Proxy
// Função única + rewrites em vercel.json fazem o roteamento.
// (anteriormente era catch-all [...path].js, mas tinha bug de routing multi-segmento)
//
// Endpoints (via rewrite):
//   GET  /api/cf/_health       -> { ok, version, hasKey }
//   ANY  /api/cf/<path>        -> forward pra https://api.curseforge.com/v1/<path>

export const config = {
  runtime: 'nodejs',
};

const CF_BASE = 'https://api.curseforge.com/v1';
const VERSION = '1.0.0';

// =============== Rate limit por IP ===============
const RL_WINDOW_MS = 60_000;
const RL_MAX = 180;
const buckets = new Map();

function rateLimit(ip) {
  const now = Date.now();
  let b = buckets.get(ip);
  if (!b || now - b.start > RL_WINDOW_MS) {
    b = { start: now, count: 0 };
    buckets.set(ip, b);
  }
  b.count += 1;
  if (b.count > RL_MAX) {
    const retryAfter = Math.ceil((RL_WINDOW_MS - (now - b.start)) / 1000);
    return { ok: false, retryAfter };
  }
  return { ok: true };
}

let lastCleanup = Date.now();
function maybeCleanup() {
  if (Date.now() - lastCleanup < 5 * 60_000) return;
  lastCleanup = Date.now();
  const cutoff = Date.now() - RL_WINDOW_MS;
  for (const [ip, b] of buckets.entries()) {
    if (b.start < cutoff) buckets.delete(ip);
  }
}

function clientIp(req) {
  try {
    const h = req.headers || {};
    const xff = h['x-forwarded-for'];
    if (xff) return String(xff).split(',')[0].trim();
    if (h['x-real-ip']) return h['x-real-ip'];
    if (req.socket && req.socket.remoteAddress) return req.socket.remoteAddress;
  } catch (_) { /* fallthrough */ }
  return 'unknown';
}

function applyCors(res) {
  try {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Accept, User-Agent');
    res.setHeader('Access-Control-Max-Age', '86400');
  } catch (_) { /* ignore */ }
}

// =============== Path extraction ===============
// Tenta pegar a URL original de várias fontes (Vercel muda o header conforme versão)
function getOriginalUrl(req) {
  const h = req.headers || {};
  return (
    h['x-vercel-original-url'] ||
    h['x-original-url'] ||
    h['x-vercel-rewrite-destination'] ||
    h['x-forwarded-uri'] ||
    req.url ||
    '/'
  );
}

// Extrai o path depois de /api/cf/ a partir da URL (com ou sem host, com ou sem query)
function extractPathFromUrl(rawUrl) {
  if (!rawUrl) return '';
  let url = String(rawUrl);
  // se tem scheme+host (URL completa tipo "https://foo.com/api/cf/x"), remove o scheme+host
  url = url.replace(/^https?:\/\/[^/]+/, '');
  // remove query string
  url = url.split('?')[0];
  // remove prefixo /api/cf (com ou sem barra no final)
  const stripped = url.replace(/^\/api\/cf\/?/, '');
  return stripped.replace(/^\/+/, '').replace(/\/+$/, '');
}

function extractQueryFromUrl(rawUrl) {
  if (!rawUrl) return '';
  const url = String(rawUrl);
  const idx = url.indexOf('?');
  return idx >= 0 ? url.substring(idx) : '';
}

// =============== Handler principal ===============
export default async function handler(req, res) {
  const startTs = Date.now();
  try {
    applyCors();

    if (req.method === 'OPTIONS') {
      return res.status(204).end();
    }

    // Tenta pegar URL original via headers (Vercel rewrite)
    // Se não conseguir, usa req.url (que será o destination da rewrite, tipo /api/cf-proxy)
    const originalUrl = getOriginalUrl(req);
    const originalPath = extractPathFromUrl(originalUrl);
    const pathStr = originalPath;

    // Log SEMPRE pra debug
    console.log(
      `[cf-proxy] HIT method=${req.method} ` +
      `req.url=${req.url} ` +
      `original=${originalUrl} ` +
      `path="${pathStr}"`
    );

    // Health check — não toca upstream, não conta no rate limit
    if (pathStr === '_health' || pathStr === '_ping') {
      const hasKey = !!process.env.CURSEFORGE_API_KEY;
      console.log(`[cf-proxy] health check -> hasKey=${hasKey}`);
      return res.status(hasKey ? 200 : 503).json({
        ok: hasKey,
        version: VERSION,
        hasKey,
      });
    }

    const ip = clientIp(req);
    maybeCleanup();

    // Rate limit
    const rl = rateLimit(ip);
    if (!rl.ok) {
      res.setHeader('Retry-After', String(rl.retryAfter));
      return res.status(429).json({
        error: 'rate_limited',
        retry_after_s: rl.retryAfter,
      });
    }

    // Validação da env var
    const apiKey = process.env.CURSEFORGE_API_KEY;
    if (!apiKey) {
      console.error('[cf-proxy] misconfigured: CURSEFORGE_API_KEY missing');
      return res.status(500).json({ error: 'proxy_misconfigured' });
    }

    // Constrói URL alvo: /<path>?<query>
    const queryStr = extractQueryFromUrl(originalUrl) ||
                     (req.url && req.url.includes('?') ? '?' + req.url.split('?').slice(1).join('?') : '');
    const tail = (pathStr ? '/' + pathStr : '') + queryStr;
    const targetUrl = `${CF_BASE}${tail}`;

    // Headers: bloqueia auth do cliente, injeta key server-side
    const headers = req.headers || {};
    const fwdHeaders = {
      'x-api-key': apiKey,
      'Accept': headers['accept'] || 'application/json',
      'User-Agent': headers['user-agent'] || `MineLauncher-Proxy/${VERSION}`,
    };
    if (headers['content-type']) {
      fwdHeaders['Content-Type'] = headers['content-type'];
    }

    // Body só em métodos com payload
    const fetchOpts = { method: req.method || 'GET', headers: fwdHeaders };
    if (!['GET', 'HEAD'].includes(req.method) && req.body != null) {
      fetchOpts.body = typeof req.body === 'string'
        ? req.body
        : JSON.stringify(req.body);
    }

    // Forward
    let upstream;
    try {
      upstream = await fetch(targetUrl, fetchOpts);
    } catch (fetchErr) {
      console.error(`[cf-proxy] fetch failed: ${fetchErr.message} url=${targetUrl}`);
      return res.status(502).json({ error: 'upstream_unreachable', message: fetchErr.message });
    }

    const body = await upstream.text();
    const elapsed = Date.now() - startTs;

    console.log(`[cf-proxy] ${req.method} /${pathStr} -> ${upstream.status} (${elapsed}ms) ip=${ip}`);

    res.status(upstream.status);
    res.setHeader('Content-Type', upstream.headers.get('content-type') || 'application/json');
    res.setHeader('X-Proxy-Latency-Ms', String(elapsed));
    return res.send(body);

  } catch (err) {
    console.error(`[cf-proxy] FATAL: ${err && err.stack ? err.stack : err}`);
    try {
      return res.status(500).json({
        error: 'internal_error',
        message: (err && err.message) ? err.message : String(err),
      });
    } catch (_) { /* res já enviado */ }
  }
}
