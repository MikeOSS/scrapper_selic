package br.com.rendafixa.api;

import br.com.rendafixa.domain.*;
import br.com.rendafixa.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {
  private final BcbMarketService market; private final ProductCatalogService catalog; private final RecommendationService recommendations;
  public ApiController(BcbMarketService market, ProductCatalogService catalog, RecommendationService recommendations) { this.market = market; this.catalog = catalog; this.recommendations = recommendations; }
  @GetMapping("/market/indicators") public MarketIndicators indicators() { return market.current(); }
  @GetMapping("/products") public List<InvestmentProduct> products() { return catalog.all(); }
  @GetMapping("/health") public Map<String, String> health() { return Map.of("status", "UP"); }
  @PostMapping("/recommendations") public RecommendationResponse recommend(@Valid @RequestBody RecommendationRequest request) { return recommendations.recommend(request); }
  @PostMapping("/admin/refresh") public Map<String, Object> refresh() { return Map.of("indicators", market.refresh(), "products", catalog.all().size(), "message", "Indicadores atualizados. Produtos exigem conectores autorizados por instituição."); }

  @ExceptionHandler(NoSuchElementException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  Map<String, String> notFound(NoSuchElementException error) { return Map.of("message", error.getMessage()); }
}
