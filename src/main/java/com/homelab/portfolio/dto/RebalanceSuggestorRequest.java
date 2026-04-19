package com.homelab.portfolio.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class RebalanceSuggestorRequest {
    private Long portfolioId;
    private BigDecimal tolerancePct = new BigDecimal("1.00"); // default 1%
    private boolean useNewMoney;
    private BigDecimal newMoneyAmount = BigDecimal.ZERO;
    private boolean allowFractionalShares;
    private String tsvPrices; // Optional fresh prices paste
}
