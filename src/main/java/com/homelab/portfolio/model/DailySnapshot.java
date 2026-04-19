package com.homelab.portfolio.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Records the price of a holding on a specific date.
 * One row is inserted per holding per trading day by the scheduler.
 */
@Entity
@Table(
    name = "daily_snapshot",
    uniqueConstraints = @UniqueConstraint(columnNames = {"holding_id", "snapshot_date"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "holding_id", nullable = false)
    private Holding holding;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    /** Closing / current price at time of snapshot */
    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal price;

    /** Total value of this holding at snapshot time (price × shares) */
    @Column(nullable = false, precision = 24, scale = 4)
    private BigDecimal totalValue;
}
