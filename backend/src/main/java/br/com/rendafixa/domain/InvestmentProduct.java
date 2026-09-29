package br.com.rendafixa.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record InvestmentProduct(
    String id, String bank, String name, InvestmentType type, RateIndex rateIndex,
    BigDecimal annualRate, BigDecimal minimumInvestment, LocalDate maturityDate,
    int liquidityDays, boolean fgcCovered, String sourceUrl, OffsetDateTime observedAt,
    String sourceStatus) { }
