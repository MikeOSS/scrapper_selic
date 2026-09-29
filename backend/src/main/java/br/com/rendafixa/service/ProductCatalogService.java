package br.com.rendafixa.service;

import br.com.rendafixa.domain.InvestmentProduct;
import br.com.rendafixa.domain.InvestmentType;
import br.com.rendafixa.domain.RateIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ProductCatalogService {
  private static final Logger log = LoggerFactory.getLogger(ProductCatalogService.class);
  private static final String DATASET_PAGE = "https://www.tesourotransparente.gov.br/ckan/dataset/taxas-dos-titulos-ofertados-pelo-tesouro-direto";
  private static final String CSV_URL = "https://www.tesourotransparente.gov.br/ckan/dataset/df56aa42-484a-4a59-8184-7676580c81e3/resource/796d2059-14e9-44e3-80c9-2d9e30b405c1/download/precotaxatesourodireto.csv";
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private List<InvestmentProduct> products = List.of();
  private OffsetDateTime lastFetch;

  public synchronized List<InvestmentProduct> all() {
    // The source is a daily dataset; refresh every four hours to avoid repeatedly
    // downloading the full historical CSV while still picking up intraday updates.
    if (lastFetch == null || lastFetch.plusHours(4).isBefore(OffsetDateTime.now())) {
      try {
        refresh();
      } catch (Exception exception) {
        log.warn("Could not refresh Tesouro Direto offers: {}", exception.getMessage());
        if (products.isEmpty()) {
          throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
              "As taxas oficiais do Tesouro Direto estão indisponíveis no momento.");
        }
        return products.stream().map(product -> withStatus(product,
            product.sourceStatus() + " · usando última coleta válida; atualização atual indisponível"))
            .toList();
      }
    }
    return products;
  }

  public synchronized int refresh() {
    List<InvestmentProduct> fresh = downloadLatestOffers();
    if (fresh.isEmpty()) throw new IllegalStateException("O arquivo oficial não contém títulos vigentes.");
    products = fresh;
    lastFetch = OffsetDateTime.now();
    return products.size();
  }

  private List<InvestmentProduct> downloadLatestOffers() {
    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) URI.create(CSV_URL).toURL().openConnection();
      connection.setConnectTimeout(10_000);
      connection.setReadTimeout(30_000);
      connection.setRequestProperty("User-Agent", "RendaFixaRadar/1.0 (public Treasury data)");

      List<InvestmentProduct> latestOffers = new ArrayList<>();
      LocalDate latestDate = null;
      OffsetDateTime fetchedAt = OffsetDateTime.now();
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        boolean header = true;
        while ((line = reader.readLine()) != null) {
          if (header) { header = false; continue; }
          String[] fields = line.split(";", -1);
          if (fields.length < 8) continue;

          LocalDate baseDate;
          try { baseDate = LocalDate.parse(fields[2].trim(), DATE_FORMAT); }
          catch (Exception ignored) { continue; }

          // The official CSV is ordered newest-first. Once an older base date
          // appears, all offers for the newest date have already been read.
          if (latestDate != null && baseDate.isBefore(latestDate)) break;
          if (latestDate == null || baseDate.isAfter(latestDate)) {
            latestDate = baseDate;
            latestOffers.clear();
          }
          if (!baseDate.equals(latestDate)) continue;

          InvestmentProduct offer = toProduct(fields, baseDate, fetchedAt);
          if (offer != null) latestOffers.add(offer);
        }
      }
      if (latestDate == null) throw new IllegalStateException("Não foi encontrada uma data-base válida no arquivo oficial.");
      List<InvestmentProduct> currentOffers = latestOffers.stream()
          .filter(offer -> offer.maturityDate().isAfter(LocalDate.now()))
          .sorted(Comparator.comparing(InvestmentProduct::maturityDate)).toList();
      return currentOffers;
    } catch (Exception exception) {
      throw new IllegalStateException("Falha ao ler os dados públicos do Tesouro Direto.", exception);
    } finally {
      if (connection != null) connection.disconnect();
    }
  }

  private InvestmentProduct toProduct(String[] fields, LocalDate baseDate, OffsetDateTime fetchedAt) {
    String titleType = fields[0].trim();
    RateIndex index;
    if (titleType.equals("Tesouro Selic")) index = RateIndex.SELIC;
    else if (titleType.equals("Tesouro Prefixado")) index = RateIndex.PREFIXED;
    else if (titleType.equals("Tesouro IPCA+")) index = RateIndex.IPCA_PLUS;
    else return null;

    try {
      LocalDate maturity = LocalDate.parse(fields[1].trim(), DATE_FORMAT);
      BigDecimal rate = decimal(fields[3]);
      BigDecimal unitPrice = decimal(fields[5]);
      String name = titleType + " · " + maturity.getYear();
      String id = "tesouro-" + index.name().toLowerCase() + "-" + maturity.toString();
      String status = "Tesouro Transparente · taxa de compra · base " + DATE_FORMAT.format(baseDate);
      return new InvestmentProduct(id, "Tesouro Nacional", name, InvestmentType.TESOURO_DIRETO, index,
          rate, unitPrice.movePointLeft(2), maturity, 1, false, DATASET_PAGE, fetchedAt, status);
    } catch (Exception ignored) {
      return null;
    }
  }

  private BigDecimal decimal(String value) {
    return new BigDecimal(value.trim().replace(".", "").replace(",", "."));
  }

  private InvestmentProduct withStatus(InvestmentProduct product, String status) {
    return new InvestmentProduct(product.id(), product.bank(), product.name(), product.type(), product.rateIndex(),
        product.annualRate(), product.minimumInvestment(), product.maturityDate(), product.liquidityDays(),
        product.fgcCovered(), product.sourceUrl(), product.observedAt(), status);
  }
}
