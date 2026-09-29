# Renda Fixa Radar

Aplicação para comparar produtos de renda fixa, consultar SELIC/IPCA no Banco Central e gerar uma análise explicável para um valor de aporte.

## O que o projeto faz

- Consulta SELIC e IPCA diretamente na API pública SGS do Banco Central.
- Atualiza o catálogo com taxas e preços públicos diários de compra do Tesouro Direto, incluindo emissor, vencimento, preço mínimo e fonte.
- Calcula retorno líquido estimado, retorno real estimado, risco e adequação ao perfil antes de usar IA.
- Usa Gemini apenas para transformar os dados calculados em uma explicação; a recomendação não depende de uma resposta não verificável do modelo.
- Expõe uma rotina de atualização preparada para conectores de bancos. Cada conector deve respeitar os termos de uso e a origem pública do banco.

> Aviso: isto é uma ferramenta educacional e comparativa, não recomendação de investimento. Preços, taxas e disponibilidade mudam; confirme tudo na página do emissor antes de aplicar.

## Limite importante sobre "todos os bancos"

Não existe uma API pública única com as ofertas de todos os bancos brasileiros. O catálogo atual usa os dados abertos oficiais do Tesouro Transparente; Selic e IPCA vêm das séries SGS do Banco Central. Ofertas de CDB, LCI e LCA de cada banco exigem fontes públicas verificáveis ou integração autorizada com um provedor. O sistema mostra a fonte e a data-base das taxas.

## Executar

Pré-requisitos: Java 21+ e Node.js 20+.

1. Crie `backend/.env` a partir de `backend/.env.example` e informe a chave `GEMINI_API_KEY`.
2. Em um terminal:

```powershell
cd backend
mvn spring-boot:run
```

3. Em outro terminal:

```powershell
cd frontend
npm install
npm run dev
```

Abra `http://localhost:3000`. A API roda em `http://localhost:8080`.

## Endpoints principais

- `GET /api/market/indicators`
- `GET /api/products`
- `POST /api/recommendations` — corpo: `{ amount, horizonMonths, riskProfile, maxLiquidityDays }`
- `POST /api/admin/refresh` — atualiza indicadores do BCB e taxas do Tesouro Direto

O backend aceita `GEMINI_API_KEY` como variável do sistema; para desenvolvimento local também lê `backend/.env`.

## Publicação

1. No Render, escolha **New + → Blueprint**, conecte este repositório e aceite o `render.yaml`. Ele cria o serviço Docker do backend com health check em `/api/health`.
2. Durante a criação, informe `GEMINI_API_KEY` e `CORS_ALLOWED_ORIGINS` (a URL do site na Vercel, sem barra no fim) como segredos. O Render fornece a URL pública do backend.
3. Na Vercel, configure `NEXT_PUBLIC_API_URL=https://URL-DO-BACKEND/api` e faça o redeploy.

Não use `localhost` nem a chave Gemini no frontend/Vercel: a chave fica somente no backend.
