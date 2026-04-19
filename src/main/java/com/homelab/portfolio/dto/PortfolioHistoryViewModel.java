package com.homelab.portfolio.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * View model for portfolio history page.
 */
@Data
@Builder
public class PortfolioHistoryViewModel {
    private Long portfolioId;
    private String portfolioName;
    private List<PortfolioHistoryEntry> history;
}
