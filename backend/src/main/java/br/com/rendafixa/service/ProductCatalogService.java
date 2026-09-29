package br.com.rendafixa.service;

import br.com.rendafixa.domain.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ProductCatalogService {
  private final List<InvestmentProduct> products = new ArrayList<>();

  public ProductCatalogService() { loadReferenceCatalog(); }

  public synchronized List<InvestmentProduct> all() {
    return products.stream().sorted(Comparator.comparing(InvestmentProduct::bank).thenComparing(InvestmentProduct::name)).toList();
  }

  public synchronized void replaceImportedProducts(List<InvestmentProduct> imported) {
    if (!imported.isEmpty()) { products.removeIf(p -> p.sourceStatus().equals("IMPORTADO")); products.addAll(imported); }
  }

  private void loadReferenceCatalog() {
    OffsetDateTime now = OffsetDateTime.now();
    // Taxas de referência para testar a análise. Conectores autorizados devem substituir por ofertas vigentes.
    products.add(product("inter-cdb", "Banco Inter", "CDB Pós-fixado", InvestmentType.CDB, RateIndex.CDI, "100", "1", 24, 1, true, "https://inter.co/pra-voce/investimentos/renda-fixa/", now));
    products.add(product("nubank-cdb", "Nubank", "CDB com liquidez diária", InvestmentType.CDB, RateIndex.CDI, "100", "1", 24, 0, true, "https://nubank.com.br/nubank-investimentos/", now));
    products.add(product("itau-cdb", "Itaú", "CDB DI", InvestmentType.CDB, RateIndex.CDI, "100", "100", 24, 1, true, "https://www.itau.com.br/investimentos-previdencia/renda-fixa/cdb", now));
    products.add(product("btg-cdb", "BTG Pactual", "CDB Pós-fixado", InvestmentType.CDB, RateIndex.CDI, "102", "1000", 36, 1, true, "https://www.btgpactual.com/investimentos/renda-fixa/cdb", now));
    products.add(product("bb-cdb", "Banco do Brasil", "CDB Pós-fixado", InvestmentType.CDB, RateIndex.CDI, "100", "500", 24, 1, true, "https://www.bb.com.br/site/investimentos/renda-fixa/", now));
    products.add(product("c6-cdb", "C6 Bank", "CDB Pós-fixado", InvestmentType.CDB, RateIndex.CDI, "103", "20", 36, 1, true, "https://www.c6bank.com.br/investimentos/renda-fixa/", now));
    products.add(product("inter-lci", "Banco Inter", "LCI", InvestmentType.LCI, RateIndex.CDI, "91", "1000", 24, 90, true, "https://inter.co/pra-voce/investimentos/renda-fixa/", now));
    products.add(product("btg-lca", "BTG Pactual", "LCA", InvestmentType.LCA, RateIndex.CDI, "92", "1000", 24, 90, true, "https://www.btgpactual.com/investimentos/renda-fixa/lca", now));
    products.add(product("itau-prefix", "Itaú", "CDB Prefixado", InvestmentType.CDB, RateIndex.PREFIXED, "12.50", "100", 36, 365, true, "https://www.itau.com.br/investimentos-previdencia/renda-fixa/cdb", now));
    products.add(product("bb-ipca", "Banco do Brasil", "CDB IPCA+", InvestmentType.CDB, RateIndex.IPCA_PLUS, "6.00", "1000", 48, 720, true, "https://www.bb.com.br/site/investimentos/renda-fixa/", now));
  }

  private InvestmentProduct product(String id, String bank, String name, InvestmentType type, RateIndex index,
      String rate, String minimum, int months, int liquidity, boolean fgc, String url, OffsetDateTime at) {
    return new InvestmentProduct(id, bank, name, type, index, new BigDecimal(rate), new BigDecimal(minimum),
        LocalDate.now().plusMonths(months), liquidity, fgc, url, at, "REFERÊNCIA — confirmar no emissor");
  }
}
