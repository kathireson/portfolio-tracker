package com.homelab.portfolio.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single holding in the portfolio.
 * <p>
 * targetAllocationPct is the desired weight of this holding expressed as a
 * percentage (e.g. 25.0 means 25%). All target allocations in the portfolio
 * should sum to 100.
 * <p>
 * For ALLOCATED holdings, targetAllocationPct should sum to 100 across the portfolio.
 * For CASH or UNTRACKED holdings, targetAllocationPct is typically 0 (or ignored).
 */
@Entity
@Table(
    name = "holding",
    uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "ticker"})
)
@Data
@Builder
@AllArgsConstructor
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Reference to the portfolio this holding belongs to */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "portfolio_id", nullable = true)
    private Portfolio portfolio;

    /** Ticker symbol, e.g. "AAPL", "VOO", "BTC-USD" */
    @NotBlank
    @Column(nullable = false, length = 20)
    private String ticker;

    /** Human-friendly name, e.g. "Apple Inc." */
    @Column(length = 100)
    private String name;

    /** Number of shares (or units) currently owned */
    @NotNull
    @DecimalMin("0.0")
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal sharesOwned;

    /**
     * Target allocation as a percentage of total portfolio value.
     * Must be between 0 and 100. All ALLOCATED holdings should sum to 100.
     * For CASH and UNTRACKED, this typically should be 0.
     */
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal targetAllocationPct;

    /**
     * Type of holding: ALLOCATED (tracked for rebalancing), CASH (no quotes/allocation),
     * or UNTRACKED (quoted but not tracked for allocation/drift).
     * Defaults to ALLOCATED for backward compatibility.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'ALLOCATED'")
    private HoldingType holdingType = HoldingType.ALLOCATED;

    /** Most recently fetched price (cached here for quick dashboard loads) */
    @Column(precision = 20, scale = 4)
    private BigDecimal lastPrice;

    @OneToMany(mappedBy = "holding", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("snapshotDate DESC")
    private List<DailySnapshot> snapshots = new ArrayList<>();

    // Constructors
    public Holding() {
    }

    public Holding(Long id, Portfolio portfolio, String ticker, String name, BigDecimal sharesOwned, BigDecimal targetAllocationPct, BigDecimal lastPrice) {
        if (id != null) this.id = id;
        this.portfolio = portfolio;
        this.ticker = ticker;
        this.name = name;
        this.sharesOwned = sharesOwned;
        this.targetAllocationPct = targetAllocationPct;
        this.lastPrice = lastPrice;
    }

    public Holding setPortfolio(Portfolio portfolio) {
        this.portfolio = portfolio;
        return this;
    }

    public Holding setTicker(String ticker) {
        this.ticker = ticker;
        return this;
    }

    public Holding setName(String name) {
        this.name = name;
        return this;
    }

    public Holding setSharesOwned(BigDecimal sharesOwned) {
        this.sharesOwned = sharesOwned;
        return this;
    }

    public Holding setTargetAllocationPct(BigDecimal targetAllocationPct) {
        this.targetAllocationPct = targetAllocationPct;
        return this;
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getSharesOwned() {
        return sharesOwned;
    }

    public BigDecimal getTargetAllocationPct() {
        return targetAllocationPct;
    }
}
