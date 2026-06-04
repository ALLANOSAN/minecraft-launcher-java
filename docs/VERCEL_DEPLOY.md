# Deploy do Proxy CurseForge na Vercel

O launcher precisa falar com a API do CurseForge para buscar mods, listar arquivos
e instalar modpacks. A chave da API **não pode ficar no código-fonte** (vaza no
git, no JAR, em forks). A solução é um proxy serverless na Vercel que segura a
chave server-side.

## TL;DR

1. Suba o conteúdo de `vercel-proxy/` na Vercel (root directory = `vercel-proxy`)
2. Configure a env var `CURSEFORGE_API_KEY` no painel
3. Anote a URL gerada
4. Atualize o default em `ModManager.java` (linha ~25)
5. Rebuild

Instruções detalhadas passo a passo: **[vercel-proxy/README.md](vercel-proxy/README.md)**

## Por que a Vercel e não outra plataforma

| Plataforma | Por que não |
|---|---|
| Heroku | Plano gratuito morreu em 2022 |
| Railway | $5/mês mínimo |
| Render | Free tier hiberna após 15 min (cold start ruim) |
| Cloudflare Workers | $0 também, mas configuração menos amigável |
| **Vercel** | **$0/mês, free tier generoso, deploy em 3 min** |

## Custo

**$0/mês** dentro do free tier para uso pessoal.

## Verificação pós-deploy

```bash
# 1. Health check
curl https://SEU-PROJETO.vercel.app/api/cf/_health
# Esperado: {"ok":true,"version":"1.0.0","hasKey":true}

# 2. Teste real (busca de mods — Minecraft = gameId 432)
curl -H "User-Agent: MineLauncher/1.0" \
  "https://SEU-PROJETO.vercel.app/api/cf/mods/search?gameId=432&searchFilter=optifine&pageSize=5"

# 3. Se o launcher estiver rodando, a aba de mods do CurseForge deve carregar
```

## Em caso de problema

Veja a seção **Troubleshooting** no [vercel-proxy/README.md](vercel-proxy/README.md).
