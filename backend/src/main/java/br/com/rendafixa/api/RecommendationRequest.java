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
    @NotNull Boolean liquidityNeed) { }
