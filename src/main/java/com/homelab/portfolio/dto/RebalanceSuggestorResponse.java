package com.homelab.portfolio.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class RebalanceSuggestorResponse {

    private Long portfolioId;
    private String portfolioName;
    private List<RebalanceAction> buys;
    private List<RebalanceAction> sells;
    private List<ProjectedHoldingRow> projectedHoldings;
    
    // Total value of the portfolio after rebalancing (includes new cash added but not unused cash)
    private BigDecimal expectedInvestedValue;
    
    // Cash that could not be deployed (especially if fractional shares are disallowed or unused new money)
    private BigDecimal remainingCash;

    @Data
    @Builder
    public static class RebalanceAction {
        private String ticker;
        private String actionType; // "BUY" or "SELL"
        private BigDecimal shares;
        private BigDecimal estimatedPrice;
        private BigDecimal totalValue;
    }

    @Data
    @Builder
    public static class ProjectedHoldingRow {
        private String ticker;
        private BigDecimal finalShares;
        private BigDecimal estimatedPrice;
        private BigDecimal finalValue;
        private BigDecimal targetAllocationPct;
        private BigDecimal projectedAllocationPct;
        private BigDecimal driftPct;
    }
}
