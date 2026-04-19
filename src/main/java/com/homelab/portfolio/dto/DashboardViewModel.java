package com.homelab.portfolio.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * View model for a single portfolio's dashboard.
 */
@Data
@Builder
public class DashboardViewModel {

    /** Portfolio ID */
    private Long portfolioId;

    /** Portfolio name */
    private String portfolioName;

    /** Total portfolio value as of lastUpdated (allocated + untracked + cash) */
    private BigDecimal totalValue;

    /** Sum of allocated holdings (those with target allocations tracked for rebalancing) */
    private BigDecimal allocatedValue;

    /** Sum of untracked holdings (quoted but not tracked for allocation %) */
    private BigDecimal untrackedValue;

    /** Sum of cash holdings (no quotes, just value) */
    private BigDecimal cashValue;

    /** Sum of all target allocations for ALLOCATED holdings — should be 100, shown as a sanity check */
    private BigDecimal totalTargetPct;

    /** When quotes were last fetched */
    private LocalDateTime lastUpdated;

    /** The date the snapshots were taken (today for live, historical otherwise) */
    private LocalDate snapshotDate;

    /** One row per ALLOCATED holding, sorted by current value descending */
    private List<HoldingRow> holdings;

    /** One row per CASH holding, sorted by current value descending */
    private List<HoldingRow> cashHoldings;

    /** One row per UNTRACKED holding, sorted by current value descending */
    private List<HoldingRow> untrackedHoldings;

    /** True if we have quote data for today already */
    private boolean quotesLoadedToday;
}
