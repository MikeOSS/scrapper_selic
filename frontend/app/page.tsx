"use client";

import { FormEvent, useEffect, useState } from "react";

const API = process.env.NEXT_PUBLIC_API_URL ?? "https://renda-fixa-radar-api.onrender.com/api";
type Indicators = { selicAnnual: number; ipcaAnnual: number; updatedAt: string; live: boolean };
type Product = { id: string; bank: string; name: string; type: string; rateIndex: string; annualRate: number; minimumInvestment: number; maturityDate: string; liquidityDays: number; fgcCovered: boolean; sourceUrl: string; observedAt: string; sourceStatus: string };
type Result = { recommendation: Product; estimatedNetReturn: number; estimatedRealReturn: number; riskScore: number; rationale: string; aiExplanation: string; warnings: string[] };
const currency = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

export default function Home() {
  const [indicators, setIndicators] = useState<Indicators | null>(null);
  const [products, setProducts] = useState<Product[]>([]);
  const [amount, setAmount] = useState("1000");
  const [months, setMonths] = useState("12");
  const [profile, setProfile] = useState("CONSERVADOR");
  const [liquidityDays, setLiquidityDays] = useState("30");
  const [result, setResult] = useState<Result | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!API) { setError("A API Java ainda não foi configurada nesta publicação."); return; }
    Promise.all([fetch(`${API}/market/indicators`), fetch(`${API}/products`)])
      .then(async ([marketResponse, productsResponse]) => {
        if (marketResponse.ok) setIndicators(await marketResponse.json());
        if (productsResponse.ok) setProducts(await productsResponse.json());
      })
      .catch(() => setError("Não foi possível conectar à API de investimentos."));
  }, []);

  async function analyze(event: FormEvent) {
    event.preventDefault();
    if (!API) { setError("Configure NEXT_PUBLIC_API_URL na Vercel após hospedar o backend Java."); return; }
    setLoading(true);
    setError("");
    try {
      const response = await fetch(`${API}/recommendations`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          amount: Number(amount.replace(",", ".")),
          horizonMonths: Number(months),
          riskProfile: profile,
          maxLiquidityDays: Number(liquidityDays),
        }),
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.message ?? "Não foi possível analisar o investimento.");
      setResult(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Erro inesperado.");
    } finally {
      setLoading(false);
    }
  }

  return <main>
    <section className="metrics">
      <Metric label="SELIC anual" value={indicators ? `${indicators.selicAnnual.toLocaleString("pt-BR")}%` : "—"} note={indicators?.live ? "Atualizado pelo BCB" : "Dados em cache / indisponíveis"} />
      <Metric label="IPCA (12 meses)" value={indicators ? `${indicators.ipcaAnnual.toLocaleString("pt-BR")}%` : "—"} note="Série SGS 13522" />
      <Metric label="Produtos no catálogo" value={String(products.length)} note="Confirme a oferta no emissor" />
    </section>

    <section className="workspace">
      <form onSubmit={analyze} className="panel form">
        <h2>Simular aporte</h2>
        <label>Quanto você quer aplicar?
          <div className="money"><span>R$</span><input inputMode="decimal" value={amount} onChange={e => setAmount(e.target.value)} required /></div>
        </label>
        <label>Prazo desejado
          <select value={months} onChange={e => setMonths(e.target.value)}>
            <option value="3">3 meses</option><option value="6">6 meses</option><option value="12">12 meses</option><option value="24">24 meses</option><option value="36">36 meses</option><option value="60">5 anos</option>
          </select>
        </label>
        <label>Seu perfil
          <select value={profile} onChange={e => setProfile(e.target.value)}>
            <option value="CONSERVADOR">Conservador</option><option value="MODERADO">Moderado</option><option value="ARROJADO">Arrojado</option>
          </select>
        </label>
        <label htmlFor="liquidity-days">Em quantos dias você precisa poder resgatar?</label>
        <input id="liquidity-days" type="number" min="0" max="36500" step="1" value={liquidityDays} onChange={e => setLiquidityDays(e.target.value)} required aria-describedby="liquidity-help" />
        <small id="liquidity-help">Informe o prazo máximo. Use 0 para liquidez diária.</small>
        <button disabled={loading}>{loading ? "Analisando…" : "Analisar opções"}</button>
        {error && <p className="error">{error}</p>}
      </form>

      <div className="panel result">
        <span className="eyebrow">MELHOR ENCAIXE CALCULADO</span>
        {result ? <>
          <h2>{result.recommendation.name}</h2>
          <p className="bank">{result.recommendation.bank} · {result.recommendation.annualRate}% {formatIndex(result.recommendation.rateIndex)}</p>
          <div className="return">
            <div><small>Retorno líquido estimado</small><strong>{currency.format(result.estimatedNetReturn)}</strong></div>
            <div><small>Retorno real estimado</small><strong>{currency.format(result.estimatedRealReturn)}</strong></div>
            <div><small>Risco calculado</small><strong>{result.riskScore}/100</strong></div>
          </div>
          <p>{result.rationale}</p>
          <div className="ai"><b>Leitura da IA</b><p>{result.aiExplanation}</p></div>
          <a href={result.recommendation.sourceUrl} target="_blank" rel="noreferrer">Conferir fonte do emissor ↗</a>
          <ul>{result.warnings.map(warning => <li key={warning}>{warning}</li>)}</ul>
        </> : <div className="empty">Informe o aporte e as preferências para receber uma análise comparativa.</div>}
      </div>
    </section>

    <section className="catalog">
      <div><span className="eyebrow">CATÁLOGO OFICIAL</span><h2>Títulos do Tesouro Direto</h2></div>
      <div className="table-wrap"><table>
        <thead><tr><th>Emissor / título</th><th>Remuneração</th><th>Liquidez</th><th>Garantia</th><th>Fonte e data-base</th></tr></thead>
        <tbody>{products.map(product => <tr key={product.id}>
          <td><b>{product.bank}</b><br /><small>{product.name} · mínimo {currency.format(product.minimumInvestment)}</small></td>
          <td>{product.annualRate}% {formatIndex(product.rateIndex)}</td>
          <td>{product.liquidityDays === 0 ? "Diária" : `${product.liquidityDays} dias`}</td>
          <td>{product.type === "TESOURO_DIRETO" ? "Tesouro Nacional" : product.fgcCovered ? "Coberto*" : "Não"}</td>
          <td><a href={product.sourceUrl} target="_blank" rel="noreferrer">Ver oferta ↗</a><br /><small>{product.sourceStatus}</small></td>
        </tr>)}</tbody>
      </table></div>
      <p className="footnote">* A cobertura do FGC tem condições e limites por CPF/CNPJ e conglomerado financeiro.</p>
    </section>
  </main>;
}

function Metric({ label, value, note }: { label: string; value: string; note: string }) {
  return <div className="metric"><span>{label}</span><strong>{value}</strong><small>{note}</small></div>;
}

function formatIndex(index: string) { return index === "IPCA_PLUS" ? "IPCA+" : index; }
