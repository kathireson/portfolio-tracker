package com.homelab.portfolio.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Persists pre-calculated aggregate daily snapshot totals for a specific portfolio
 * or for all portfolios combined (when portfolio is null).
 */
@Entity
@Table(
    name = "portfolio_daily_snapshot",
    uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "snapshot_date"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioDailySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The portfolio this aggregate snapshot belongs to.
     * Null represents the grand total across ALL portfolios.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = true)
    private Portfolio portfolio;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "allocated_value", nullable = false, precision = 24, scale = 4)
    private BigDecimal allocatedValue;

    @Column(name = "untracked_value", nullable = false, precision = 24, scale = 4)
    private BigDecimal untrackedValue;

    @Column(name = "cash_value", nullable = false, precision = 24, scale = 4)
    private BigDecimal cashValue;

    @Column(name = "total_value", nullable = false, precision = 24, scale = 4)
    private BigDecimal totalValue;
}
