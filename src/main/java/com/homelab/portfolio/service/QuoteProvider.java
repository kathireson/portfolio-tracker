package com.homelab.portfolio.service;

import java.math.BigDecimal;

/**
 * Strategy interface for fetching a real-time (or delayed) quote.
 * Swap implementations via the portfolio.quote-provider property.
 */
public interface QuoteProvider {

    /**
     * Fetch the latest price for the given ticker.
     *
     * @param ticker e.g. "AAPL", "VOO", "BTC-USD"
     * @return current price, or null if the ticker is unknown / unreachable
     */
    BigDecimal fetchPrice(String ticker);
}
