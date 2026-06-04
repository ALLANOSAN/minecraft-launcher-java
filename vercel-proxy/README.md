# MineLauncher — CurseForge Proxy

Serverless proxy serverless (Vercel) que injeta a chave da API CurseForge nas
requisições do launcher desktop. A chave **nunca** sai do servidor.

## Por que existe

A API do CurseForge exige o header `x-api-key`. As alternativas ruins:

| Abordagem | Problema |
|---|---|
| Hardcoded no JAR | Chave vaza no git, no JAR extraível, em forks |
| Variável de ambiente no cliente | Usuário precisa configurar; não escala |
| Properties bundled no JAR | Mesma exposição do hardcoded |
| **Este proxy** | Chave criptografada na Vercel, rotacionável sem rebuild |

## Estrutura

```
vercel-proxy/
├── api/cf/[...path].js    # função serverless
├── package.json
├── vercel.json            # config (maxDuration 10s)
├── .env.example           # template da env var
└── README.md              # este arquivo
```

## Deploy na Vercel

### 1. Pelo painel (mais simples, ~3 min)

1. Acesse <https://vercel.com/new>
2. Importe o repositório `minecraft-launcher-java`
3. Configure:
   - **Project Name**: `minelauncher-proxy` (ou o nome que quiser)
   - **Root Directory**: `vercel-proxy` ← IMPORTANTE
   - **Framework Preset**: Other
   - **Build Command**: *(vazio)*
   - **Output Directory**: *(vazio)*
   - **Install Command**: *(padrão — `npm install` não faz nada pois não há deps)*
4. Clique em **Deploy** (vai falhar na primeira vez porque falta a env var — tudo bem)
5. Vá em **Settings → Environment Variables**
6. Adicione:
   - **Key**: `CURSEFORGE_API_KEY`
   - **Value**: sua chave do CurseForge
   - **Environments**: marque Production, Preview, Development
7. Volte em **Deployments** e clique em **Redeploy** no commit mais recente

### 2. Pela CLI

```bash
cd vercel-proxy
npm i -g vercel   # primeira vez
vercel login
vercel            # cria projeto + deploy de preview
vercel env add CURSEFORGE_API_KEY production   # cola a chave
vercel --prod     # deploy de produção
```

## Após o deploy

1. Anote a URL: `https://minelauncher-proxy.vercel.app` (ou o nome que você escolheu)
2. Teste:
   ```bash
   curl https://minelauncher-proxy.vercel.app/api/cf/_health
   # Esperado: {"ok":true,"version":"1.0.0","hasKey":true}
   ```
3. Edite `src/main/java/com/minelauncher/mods/ModManager.java`, linha ~25:
   ```java
   private static final String CURSEFORGE_PROXY_URL = System.getenv().getOrDefault(
           "CURSEFORGE_PROXY_URL",
           "https://SEU-PROJETO-REAL.vercel.app/api/cf"  // ← troque aqui
   );
   ```
4. Rebuild: `mvn clean package`

## Testar localmente

```bash
cd vercel-proxy
cp .env.example .env
# edite .env e coloque sua chave real
vercel dev
# Servirá em http://localhost:3000
# Teste: curl http://localhost:3000/api/cf/_health
```

## Limites do free tier da Vercel

| Recurso | Limite | Impacto |
|---|---|---|
| Function timeout | 10s | Chamadas CF lentas podem estourar em modpacks enormes |
| Cold start | ~200-500ms | Irrelevante |
| Compute | 100 GB-h/mês | Mais que suficiente |
| Bandwidth | 1 TB/mês | Mais que suficiente |

**Custo real: $0/mês** para uso pessoal típico.

## Segurança

- A chave fica criptografada nos servidores da Vercel (AES-256)
- O proxy **bloqueia** headers de auth vindos do cliente (defesa em profundidade)
- Rate limit: 180 req/min por IP, retorna 429 com `Retry-After`
- Logs registram método/path/status/latência/IP — **nunca** body ou headers
- Variáveis de ambiente são scoped por ambiente (Production/Preview/Development)

## Rotação da chave

Se a chave do CurseForge vazar ou expirar:

1. Gere/obtenha nova chave
2. Vercel → Settings → Environment Variables → `CURSEFORGE_API_KEY` → edite
3. Deployments → clique **Redeploy**
4. Pronto. Launcher continua funcionando sem rebuild.

## Troubleshooting

| Sintoma | Causa provável |
|---|---|
| `503 {"ok":false,"hasKey":false}` | Env var não foi setada ou ainda não fez redeploy |
| `429 rate_limited` | Muitas chamadas — espere 60s ou ajuste `RL_MAX` |
| `502 upstream_error` | CurseForge fora do ar ou timeout |
| `500 proxy_misconfigured` | `CURSEFORGE_API_KEY` vazia na env |

## Logs

Vercel Dashboard → projeto → aba **Logs**. Formato:

```
[cf-proxy] GET /mods/123/files -> 200 (245ms) ip=189.4.x.x
[cf-proxy] POST /mods -> 201 (1.2s) ip=189.4.x.x
[cf-proxy] upstream error: fetch failed url=https://api.curseforge.com/v1/mods/...
```
