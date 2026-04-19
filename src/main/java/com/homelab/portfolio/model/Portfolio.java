package com.homelab.portfolio.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a portfolio held at a specific brokerage or account.
 * For example: "Fidelity" or "Robinhood" or "Schwab".
 */
@Entity
@Table(name = "portfolio")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Name of the account/brokerage, e.g. "Fidelity Brokerage" */
    @NotBlank
    @Column(nullable = false, length = 100)
    private String name;

    /** Optional description */
    @Column(length = 255)
    private String description;

    /** Display order, lower numbers first */
    @Builder.Default
    @Column(nullable = false)
    private Integer displayOrder = 0;

    /** One-to-many relationship to holdings in this portfolio */
    @OneToMany(mappedBy = "portfolio", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<Holding> holdings = new ArrayList<>();

    /**
     * Setter for holdings used during entity construction
     * @param holdings the list of holdings
     * @return this for method chaining
     */
    public Portfolio setHoldings(List<Holding> holdings) {
        this.holdings = holdings;
        return this;
    }

    /**
     * Setter for name used during entity construction
     * @param name the portfolio name
     * @return this for method chaining
     */
    public Portfolio setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Setter for displayOrder used during entity construction
     * @param displayOrder the display order
     * @return this for method chaining
     */
    public Portfolio setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
        return this;
    }

    /**
     * Get holdings for this portfolio
     * @return list of holdings
     */
    public List<Holding> getHoldings() {
        return holdings;
    }
}
