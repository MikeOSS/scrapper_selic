package br.com.rendafixa.service;

import br.com.rendafixa.api.*;
import br.com.rendafixa.domain.*;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class RecommendationService {
  private final ProductCatalogService catalog;
  private final BcbMarketService market;
  private final GeminiExplanationService gemini;

  public RecommendationService(ProductCatalogService catalog, BcbMarketService market, GeminiExplanationService gemini) {
    this.catalog = catalog; this.market = market; this.gemini = gemini;
  }

  public RecommendationResponse recommend(RecommendationRequest request) {
    MarketIndicators indicators = market.current();
    List<ScoredProduct> eligible = catalog.all().stream()
        .filter(p -> p.minimumInvestment().compareTo(request.amount()) <= 0)
        .filter(p -> !request.liquidityNeed() || p.liquidityDays() <= 30)
        .filter(p -> ChronoUnit.MONTHS.between(LocalDate.now(), p.maturityDate()) >= request.horizonMonths())
        .map(p -> evaluate(p, request, indicators)).sorted(Comparator.comparingDouble(ScoredProduct::score).reversed()).toList();
    if (eligible.isEmpty()) throw new NoSuchElementException("Não há produto do catálogo compatível com aporte, prazo e liquidez selecionados.");
    ScoredProduct best = eligible.getFirst();
    String ai = gemini.explain(request, best.product, best.rationale, best.riskScore);
    List<String> warnings = new ArrayList<>(List.of("Taxas do catálogo de referência devem ser confirmadas no emissor antes de investir.",
        "FGC tem limites por CPF/CNPJ e conglomerado; acompanhe sua exposição total."));
    if (!indicators.live()) warnings.add("Indicadores do Banco Central estão em cache ou temporariamente indisponíveis.");
    if (request.amount().compareTo(new BigDecimal("250000")) > 0) warnings.add("O aporte supera R$ 250 mil: não presuma cobertura integral do FGC.");
    return new RecommendationResponse(best.product, money(best.netReturn), money(best.realReturn), best.riskScore, best.rationale, ai, warnings);
  }

  private ScoredProduct evaluate(InvestmentProduct p, RecommendationRequest request, MarketIndicators market) {
    BigDecimal annual = switch (p.rateIndex()) {
      case CDI -> market.selicAnnual().multiply(p.annualRate()).movePointLeft(2);
      case PREFIXED -> p.annualRate();
      case IPCA_PLUS -> market.ipcaAnnual().add(p.annualRate());
    };
    BigDecimal years = BigDecimal.valueOf(request.horizonMonths()).divide(BigDecimal.valueOf(12), 6, RoundingMode.HALF_UP);
    BigDecimal gross = request.amount().multiply(BigDecimal.ONE.add(annual.movePointLeft(2)).pow(years.intValue(), new MathContext(12))
        .multiply(BigDecimal.ONE.add(annual.movePointLeft(2).multiply(BigDecimal.valueOf(years.remainder(BigDecimal.ONE).doubleValue()))))
        .subtract(request.amount());
    // IR regressivo para CDB/LC; LCI e LCA são isentas para pessoa física.
    BigDecimal taxRate = (p.type() == InvestmentType.LCI || p.type() == InvestmentType.LCA) ? BigDecimal.ZERO : taxRate(request.horizonMonths() * 30);
    BigDecimal net = gross.multiply(BigDecimal.ONE.subtract(taxRate));
    BigDecimal real = BigDecimal.ONE.add(net.divide(request.amount(), 8, RoundingMode.HALF_UP))
        .divide(BigDecimal.ONE.add(market.ipcaAnnual().movePointLeft(2).multiply(years)), 8, RoundingMode.HALF_UP)
        .subtract(BigDecimal.ONE).multiply(request.amount());
    int risk = (!p.fgcCovered() ? 38 : 12) + (p.liquidityDays() > 30 ? 18 : 4) + (p.rateIndex() == RateIndex.PREFIXED ? 11 : 4);
    if (request.riskProfile() == RiskProfile.CONSERVADOR && p.liquidityDays() > 90) risk += 15;
    risk = Math.min(risk, 100);
    double score = net.doubleValue() / Math.max(1, request.amount().doubleValue()) * 100 - risk * (request.riskProfile() == RiskProfile.CONSERVADOR ? .55 : .25);
    String rationale = "retorno líquido estimado de " + net.setScale(2, RoundingMode.HALF_UP) + ", taxa " + p.rateIndex()
        + ", liquidez em " + p.liquidityDays() + " dia(s), " + (p.fgcCovered() ? "com cobertura FGC declarada" : "sem FGC") + ".";
    return new ScoredProduct(p, score, net, real, risk, rationale);
  }

  private BigDecimal taxRate(int days) { return days <= 180 ? new BigDecimal(".225") : days <= 360 ? new BigDecimal(".20") : days <= 720 ? new BigDecimal(".175") : new BigDecimal(".15"); }
  private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
  private record ScoredProduct(InvestmentProduct product, double score, BigDecimal netReturn, BigDecimal realReturn, int riskScore, String rationale) { }
}
