package br.com.rendafixa.service;

import br.com.rendafixa.domain.MarketIndicators;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class BcbMarketService {
  private static final String BCB = "https://api.bcb.gov.br/dados/serie/bcdata.sgs.%s/dados/ultimos/1?formato=json";
  private final RestClient client = RestClient.builder().build();
  private final AtomicReference<MarketIndicators> cache = new AtomicReference<>();

  public MarketIndicators current() {
    MarketIndicators saved = cache.get();
    if (saved != null && saved.updatedAt().plusMinutes(15).isAfter(OffsetDateTime.now())) return saved;
    try {
      // SGS 432 = meta SELIC anual; 433 = IPCA acumulado em 12 meses.
      BigDecimal selic = readSeries(432);
      BigDecimal ipca = readSeries(433);
      MarketIndicators fresh = new MarketIndicators(selic, ipca, OffsetDateTime.now(), "Banco Central do Brasil (SGS)", true);
      cache.set(fresh);
      return fresh;
    } catch (Exception exception) {
      return saved != null ? new MarketIndicators(saved.selicAnnual(), saved.ipcaAnnual(), saved.updatedAt(), saved.source(), false)
          : new MarketIndicators(BigDecimal.ZERO, BigDecimal.ZERO, OffsetDateTime.now(), "BCB indisponível", false);
    }
  }

  public MarketIndicators refresh() { cache.set(null); return current(); }

  private BigDecimal readSeries(int series) {
    JsonNode body = client.get().uri(BCB.formatted(series)).retrieve().body(JsonNode.class);
    if (body == null || body.isEmpty()) throw new IllegalStateException("Série SGS sem dados: " + series);
    return new BigDecimal(body.get(0).path("valor").asText().replace(",", "."));
  }
}
