package com.homelab.portfolio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;

/**
 * Fetches quotes from Yahoo Finance's unofficial JSON endpoint.
 * No API key required — but be aware this endpoint is undocumented and
 * could break. For production use consider Alpha Vantage or Polygon.io.
 *
 * URL pattern:
 *   https://query1.finance.yahoo.com/v8/finance/chart/{ticker}?interval=1d&range=1d
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "portfolio.quote-provider", havingValue = "YAHOO", matchIfMissing = true)
public class YahooFinanceQuoteProvider implements QuoteProvider {

    private static final String BASE_URL = "https://query1.finance.yahoo.com";

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Override
    public BigDecimal fetchPrice(String ticker) {
        try {
            String json = webClientBuilder.build()
                    .get()
                    .uri(BASE_URL + "/v8/finance/chart/{ticker}?interval=1d&range=1d", ticker)
                    // Yahoo Finance requires a browser-like User-Agent
                    .header("User-Agent", "Mozilla/5.0")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (json == null) return null;

            JsonNode root = objectMapper.readTree(json);

            // Path: chart → result[0] → meta → regularMarketPrice
            JsonNode price = root
                    .path("chart")
                    .path("result")
                    .get(0)
                    .path("meta")
                    .path("regularMarketPrice");

            if (price.isMissingNode() || price.isNull()) {
                log.warn("No price found for ticker {}", ticker);
                return null;
            }

            return new BigDecimal(price.asText());

        } catch (Exception e) {
            log.error("Failed to fetch Yahoo Finance quote for {}: {}", ticker, e.getMessage());
            return null;
        }
    }
}
