package br.com.rendafixa.api;

import br.com.rendafixa.domain.RiskProfile;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RecommendationRequest(
    @NotNull @DecimalMin("1.00") BigDecimal amount,
    @NotNull @Min(1) @Max(600) Integer horizonMonths,
    @NotNull RiskProfile riskProfile,
    Boolean liquidityNeed,
    @Min(0) @Max(36500) Integer maxLiquidityDays) {

  public int requiredLiquidityDays() {
    if (maxLiquidityDays != null) return maxLiquidityDays;
    if (liquidityNeed != null) return liquidityNeed ? 30 : Integer.MAX_VALUE;
    return 30;
  }
}
