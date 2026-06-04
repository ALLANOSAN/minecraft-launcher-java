// MineLauncher — CurseForge API Proxy
// Serverless function que injeta a chave da API CurseForge em cada requisição.
// Cliente (launcher) fala com ESTE proxy; o proxy fala com CurseForge.
//
// Por que existe: hardcodar a chave no JAR vaza no git, no JAR extraível, em
// forks, em CI logs. Aqui a chave fica criptografada nos servers da Vercel.
//
// Endpoints:
//   GET  /api/cf/_health       -> { ok, version, hasKey }
//   ANY  /api/cf/<path>        -> forward pra https://api.curseforge.com/v1/<path>

// FORÇA runtime Node.js (evita cair no Edge runtime, que tem APIs diferentes)
export const config = {
  runtime: 'nodejs18.x',
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
    const xff = req.headers && req.headers['x-forwarded-for'];
    if (xff) return String(xff).split(',')[0].trim();
    if (req.headers && req.headers['x-real-ip']) return req.headers['x-real-ip'];
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
  } catch (_) { /* some responses (e.g. Edge) don't allow setHeader early */ }
}

// =============== Handler principal ===============
export default async function handler(req, res) {
  try {
    applyCors();

    if (req.method === 'OPTIONS') {
      return res.status(204).end();
    }

    // Defensivo: req.query pode ser undefined em alguns runtimes
    const query = (req && req.query) || {};
    const pathRaw = query.path;
    const pathStr = Array.isArray(pathRaw)
      ? pathRaw.join('/')
      : (typeof pathRaw === 'string' ? pathRaw : '');

    // Health check — não toca upstream, não conta no rate limit
    if (pathStr === '_health' || pathStr === '_ping') {
      const hasKey = !!process.env.CURSEFORGE_API_KEY;
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

    // Constrói URL alvo preservando path + querystring do cliente
    // req.url = "/api/cf/mods/123?gameId=432"
    // tail    = "/mods/123?gameId=432"
    const reqUrl = (req && req.url) ? String(req.url) : '/';
    const tail = reqUrl.replace(/^\/api\/cf/, '') || '/';
    const targetUrl = `${CF_BASE}${tail}`;

    // Headers: bloqueia auth do cliente, injeta key server-side
    const headers = req.headers || {};
    const fwdHeaders = {
      'x-api-key': apiKey,
      'Accept': headers['accept'] || 'application/json',
      // Passa o User-Agent do cliente (ex: "MineLauncher/1.0") se vier,
      // senão identifica o proxy. CurseForge prefere app identificado.
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
    const start = Date.now();
    let upstream;
    try {
      upstream = await fetch(targetUrl, fetchOpts);
    } catch (fetchErr) {
      console.error(`[cf-proxy] fetch failed: ${fetchErr.message} url=${targetUrl}`);
      return res.status(502).json({ error: 'upstream_unreachable', message: fetchErr.message });
    }

    const body = await upstream.text();
    const elapsed = Date.now() - start;

    // Log: método, path, status, latência, IP. NÃO loga body nem headers.
    console.log(`[cf-proxy] ${req.method} /${pathStr} -> ${upstream.status} (${elapsed}ms) ip=${ip}`);

    res.status(upstream.status);
    res.setHeader('Content-Type', upstream.headers.get('content-type') || 'application/json');
    res.setHeader('X-Proxy-Latency-Ms', String(elapsed));
    return res.send(body);

  } catch (err) {
    // Última linha de defesa: loga o erro real e retorna 500 com mensagem
    console.error(`[cf-proxy] FATAL: ${err && err.stack ? err.stack : err}`);
    try {
      return res.status(500).json({
        error: 'internal_error',
        message: (err && err.message) ? err.message : String(err),
      });
    } catch (_) {
      // res já foi enviado; não tem o que fazer
    }
  }
}
