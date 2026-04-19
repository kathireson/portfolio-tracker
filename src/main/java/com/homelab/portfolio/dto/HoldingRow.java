package com.homelab.portfolio.dto;

import com.homelab.portfolio.model.HoldingType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * View model passed to the Thymeleaf dashboard template.
 * All percentage values are in the range 0–100.
 * All dollar values are in USD (or whatever currency the prices are quoted in).
 */
@Data
@Builder
public class HoldingRow {

    private Long id;
    private String ticker;
    private String name;
    private BigDecimal sharesOwned;

    /** Type of holding: ALLOCATED, CASH, or UNTRACKED */
    private HoldingType holdingType;

    /** Latest fetched price */
    private BigDecimal currentPrice;

    /** sharesOwned × currentPrice */
    private BigDecimal currentValue;

    /** currentValue / totalAllocatedValue × 100 (only for ALLOCATED holdings) */
    private BigDecimal currentAllocationPct;

    /** From Holding.targetAllocationPct (only for ALLOCATED holdings) */
    private BigDecimal targetAllocationPct;

    /**
     * currentAllocationPct − targetAllocationPct (only for ALLOCATED holdings)
     * Positive → overweight, Negative → underweight
     */
    private BigDecimal driftPct;

    /**
     * driftPct / 100 × totalAllocatedValue (only for ALLOCATED holdings)
     * Positive → need to sell this much, Negative → need to buy this much
     */
    private BigDecimal driftDollars;
}
