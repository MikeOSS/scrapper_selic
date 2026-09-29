package br.com.rendafixa.api;

import br.com.rendafixa.domain.InvestmentProduct;
import java.math.BigDecimal;
import java.util.List;

public record RecommendationResponse(InvestmentProduct recommendation, BigDecimal estimatedNetReturn,
                                     BigDecimal estimatedRealReturn, int riskScore, String rationale,
                                     String aiExplanation, List<String> warnings) { }
