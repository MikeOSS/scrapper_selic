package br.com.rendafixa.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MarketIndicators(BigDecimal selicAnnual, BigDecimal ipcaAnnual, OffsetDateTime updatedAt,
                               String source, boolean live) { }
