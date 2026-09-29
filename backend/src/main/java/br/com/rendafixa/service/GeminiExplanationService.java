package br.com.rendafixa.service;

import br.com.rendafixa.api.RecommendationRequest;
import br.com.rendafixa.domain.InvestmentProduct;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Service
public class GeminiExplanationService {
  private final RestClient client = RestClient.builder().build();
  @Value("${gemini.api-key:}") private String configuredKey;
  @Value("${gemini.model:gemini-2.0-flash}") private String model;

  public String explain(RecommendationRequest request, InvestmentProduct product, String rationale, int riskScore) {
    String key = configuredKey == null || configuredKey.isBlank() ? localKey() : configuredKey;
    if (key == null || key.isBlank()) return "Análise por IA indisponível: configure GEMINI_API_KEY no ambiente do backend.";
    String prompt = "Você é um assistente educacional de renda fixa no Brasil. Não dê ordem de compra nem promessa de retorno. "
        + "Explique em até 90 palavras, em português, a recomendação calculada. Aporte: R$ " + request.amount()
        + "; prazo: " + request.horizonMonths() + " meses; perfil: " + request.riskProfile()
        + "; produto: " + product.bank() + " — " + product.name() + "; taxa: " + product.annualRate() + " " + product.rateIndex()
        + "; risco calculado: " + riskScore + "/100. Critérios: " + rationale
        + ". Cite liquidez, FGC e necessidade de confirmar taxa/vencimento na fonte.";
    try {
      Map<String, Object> body = Map.of("contents", new Object[]{Map.of("parts", new Object[]{Map.of("text", prompt)})},
          "generationConfig", Map.of("temperature", 0.2, "maxOutputTokens", 180));
      JsonNode result = client.post()
          .uri("https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent", model)
          .header("x-goog-api-key", key).body(body).retrieve().body(JsonNode.class);
      String text = result == null ? "" : result.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
      return text.isBlank() ? "A IA não retornou uma explicação nesta consulta." : text.trim();
    } catch (Exception ignored) {
      return "A explicação por IA ficou indisponível; a recomendação numérica continua baseada nos critérios exibidos.";
    }
  }

  private String localKey() {
    String environmentKey = System.getenv("GEMINI_API_KEY");
    if (environmentKey != null && !environmentKey.isBlank()) return environmentKey;
    try {
      return Files.readAllLines(Path.of(".env")).stream()
          .filter(line -> line.startsWith("GEMINI_API_KEY="))
          .map(line -> line.substring("GEMINI_API_KEY=".length()).trim()).findFirst().orElse("");
    } catch (Exception ignored) { return ""; }
  }
}
