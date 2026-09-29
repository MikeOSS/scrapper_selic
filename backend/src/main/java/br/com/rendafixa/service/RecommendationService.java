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
        .filter(p -> p.liquidityDays() <= request.requiredLiquidityDays())
        .filter(p -> ChronoUnit.MONTHS.between(LocalDate.now(), p.maturityDate()) >= request.horizonMonths())
        .map(p -> evaluate(p, request, indicators)).sorted(Comparator.comparingDouble(ScoredProduct::score).reversed()).toList();
    if (eligible.isEmpty()) throw new NoSuchElementException("Não há título oficial compatível com o aporte, prazo e liquidez informados.");
    ScoredProduct best = eligible.getFirst();
    String ai = gemini.explain(request, best.product, best.rationale, best.riskScore);
    List<String> warnings = new ArrayList<>(List.of("A taxa usada é a taxa de compra publicada para a data-base indicada na fonte oficial.",
        "Se vender antes do vencimento, o preço do título pode variar e o retorno pode ser diferente da estimativa."));
    warnings.add("A estimativa considera o IR e a taxa de custódia da B3, mas não eventuais taxas da corretora.");
    if (!indicators.live()) warnings.add("Indicadores do Banco Central estão em cache ou temporariamente indisponíveis.");
    return new RecommendationResponse(best.product, money(best.netReturn), money(best.realReturn), best.riskScore, best.rationale, ai, warnings);
  }

  private ScoredProduct evaluate(InvestmentProduct p, RecommendationRequest request, MarketIndicators market) {
    BigDecimal annual = switch (p.rateIndex()) {
      case CDI -> market.selicAnnual().multiply(p.annualRate()).movePointLeft(2);
      case SELIC -> market.selicAnnual().add(p.annualRate())
          .add(market.selicAnnual().multiply(p.annualRate()).movePointLeft(2));
      case PREFIXED -> p.annualRate();
      case IPCA_PLUS -> market.ipcaAnnual().add(p.annualRate())
          .add(market.ipcaAnnual().multiply(p.annualRate()).movePointLeft(2));
    };
    BigDecimal years = BigDecimal.valueOf(request.horizonMonths()).divide(BigDecimal.valueOf(12), 6, RoundingMode.HALF_UP);
    BigDecimal gross = request.amount().multiply(growthFactor(annual, years)).subtract(request.amount());
    // IR regressivo para CDB/LC; LCI e LCA são isentas para pessoa física.
    BigDecimal taxRate = (p.type() == InvestmentType.LCI || p.type() == InvestmentType.LCA) ? BigDecimal.ZERO : taxRate(request.horizonMonths() * 30);
    BigDecimal net = gross.multiply(BigDecimal.ONE.subtract(taxRate));
    if (p.type() == InvestmentType.TESOURO_DIRETO) {
      // B3 custody fee: 0.20% p.a.; Tesouro Selic is exempt for the first R$10,000.
      BigDecimal custodyBase = request.amount();
      if (p.rateIndex() == RateIndex.SELIC) {
        custodyBase = custodyBase.subtract(new BigDecimal("10000")).max(BigDecimal.ZERO);
      }
      BigDecimal custodyFee = custodyBase.multiply(new BigDecimal("0.002")).multiply(years);
      net = net.subtract(custodyFee).max(BigDecimal.ZERO);
    }
    BigDecimal real = request.amount().add(net)
        .divide(growthFactor(market.ipcaAnnual(), years), 8, RoundingMode.HALF_UP)
        .subtract(request.amount());
    int creditRisk = p.type() == InvestmentType.TESOURO_DIRETO ? 4 : (!p.fgcCovered() ? 38 : 12);
    int durationRisk = p.type() == InvestmentType.TESOURO_DIRETO && p.rateIndex() != RateIndex.SELIC
        ? Math.min(30, Math.max(0, (int) ChronoUnit.YEARS.between(LocalDate.now(), p.maturityDate())) * 2) : 0;
    int risk = creditRisk + durationRisk + (p.liquidityDays() > 30 ? 18 : 4) + (p.rateIndex() == RateIndex.PREFIXED ? 11 : 4);
    if (request.riskProfile() == RiskProfile.CONSERVADOR && p.liquidityDays() > 90) risk += 15;
    risk = Math.min(risk, 100);
    double riskPenalty = switch (request.riskProfile()) {
      case CONSERVADOR -> .55;
      case MODERADO -> .35;
      case ARROJADO -> .20;
    };
    double score = net.doubleValue() / Math.max(1, request.amount().doubleValue()) * 100 - risk * riskPenalty;
    String guarantee = p.type() == InvestmentType.TESOURO_DIRETO
        ? "título público federal, sem cobertura do FGC"
        : (p.fgcCovered() ? "com cobertura FGC declarada" : "sem cobertura FGC");
    String rationale = "retorno líquido estimado de " + net.setScale(2, RoundingMode.HALF_UP) + ", taxa " + p.rateIndex()
        + ", liquidez em " + p.liquidityDays() + " dia(s), " + guarantee + ".";
    return new ScoredProduct(p, score, net, real, risk, rationale);
  }

  private BigDecimal taxRate(int days) { return days <= 180 ? new BigDecimal(".225") : days <= 360 ? new BigDecimal(".20") : days <= 720 ? new BigDecimal(".175") : new BigDecimal(".15"); }
  private BigDecimal growthFactor(BigDecimal annualPercent, BigDecimal years) {
    double annualRate = BigDecimal.ONE.add(annualPercent.movePointLeft(2)).doubleValue();
    return BigDecimal.valueOf(Math.pow(annualRate, years.doubleValue()));
  }
  private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
  private record ScoredProduct(InvestmentProduct product, double score, BigDecimal netReturn, BigDecimal realReturn, int riskScore, String rationale) { }
}
