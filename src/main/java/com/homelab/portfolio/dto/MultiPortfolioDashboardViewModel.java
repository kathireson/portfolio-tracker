package com.homelab.portfolio.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Top-level view model for the multi-portfolio dashboard page.
 */
@Data
@Builder
public class MultiPortfolioDashboardViewModel {

    /** All portfolios' dashboards, ordered by display order */
    private List<DashboardViewModel> portfolios;

    /** Aggregate total across all portfolios */
    private BigDecimal grandTotalValue;

    /** When any quotes were last fetched */
    private LocalDateTime lastUpdated;
}
