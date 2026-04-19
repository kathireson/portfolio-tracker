package com.homelab.portfolio.model;

/**
 * Enumeration of holding types in a portfolio.
 * 
 * ALLOCATED: Standard holding tracked for allocation % and drift calculation.
 *            Contributes to portfolio rebalancing calculations.
 * 
 * CASH: Special holding representing cash/money market holdings.
 *       - No price quotes fetched
 *       - Does not contribute to allocated %
 *       - Drift not calculated
 *       - Ticker is typically "CASH"
 * 
 * UNTRACKED: Holdings that are quoted but not tracked for allocation %.
 *            - Price quotes are fetched
 *            - Contributes to portfolio value but not allocation %
 *            - Drift not calculated
 *            - Useful for auxiliary holdings (emergency fund, secondary assets, etc.)
 */
public enum HoldingType {
    ALLOCATED,    // Standard tracked allocation
    CASH,         // Cash/money market (no quotes, no allocation)
    UNTRACKED     // Quoted but not tracked for allocation/drift
}
