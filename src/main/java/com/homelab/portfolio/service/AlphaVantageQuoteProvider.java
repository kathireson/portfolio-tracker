package com.homelab.portfolio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;

/**
 * Fetches quotes from Alpha Vantage.
 * Requires a free API key from https://www.alphavantage.co/support/#api-key
 *
 * Enable by setting: portfolio.quote-provider=ALPHA_VANTAGE
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "portfolio.quote-provider", havingValue = "ALPHA_VANTAGE")
public class AlphaVantageQuoteProvider implements QuoteProvider {

    private static final String BASE_URL = "https://www.alphavantage.co";

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${portfolio.alpha-vantage.api-key}")
    private String apiKey;

    @Override
    public BigDecimal fetchPrice(String ticker) {
        try {
            String json = webClientBuilder.build()
                    .get()
                    .uri(BASE_URL + "/query?function=GLOBAL_QUOTE&symbol={ticker}&apikey={key}",
                            ticker, apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (json == null) return null;

            JsonNode root = objectMapper.readTree(json);

            // Path: "Global Quote" → "05. price"
            JsonNode price = root.path("Global Quote").path("05. price");

            if (price.isMissingNode() || price.isNull() || price.asText().isBlank()) {
                log.warn("No Alpha Vantage price found for ticker {}", ticker);
                return null;
            }

            return new BigDecimal(price.asText().trim());

        } catch (Exception e) {
            log.error("Failed to fetch Alpha Vantage quote for {}: {}", ticker, e.getMessage());
            return null;
        }
    }
}
