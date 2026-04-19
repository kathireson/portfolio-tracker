package com.homelab.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a single day's historical portfolio value.
 */
@Data
@Builder
@AllArgsConstructor
public class PortfolioHistoryEntry {
    private LocalDate date;
    
    /** Total value of allocated holdings for this date */
    private BigDecimal allocatedValue;
    
    /** Total value of untracked holdings for this date */
    private BigDecimal untrackedValue;
    
    /** Total value of cash holdings for this date */
    private BigDecimal cashValue;
    
    /** Sum of all values (allocated + untracked + cash) */
    private BigDecimal totalValue;
}
