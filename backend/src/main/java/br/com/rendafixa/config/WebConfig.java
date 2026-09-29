package br.com.rendafixa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.util.Arrays;
import java.util.stream.Stream;

@Configuration
public class WebConfig {
  @Value("${app.cors.allowed-origins}") private String allowedOrigins;

  @Bean
  WebMvcConfigurer corsConfigurer() {
    String[] allowedOriginPatterns = Stream.concat(
            Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(origin -> !origin.isEmpty()),
            Stream.of("http://localhost:3000", "https://*.vercel.app"))
        .distinct()
        .toArray(String[]::new);

    return new WebMvcConfigurer() {
      @Override public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns(allowedOriginPatterns)
            .allowedMethods("GET", "POST");
      }
    };
  }
}
