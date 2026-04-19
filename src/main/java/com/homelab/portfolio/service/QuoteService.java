package com.homelab.portfolio.service;

import com.homelab.portfolio.model.DailySnapshot;
import com.homelab.portfolio.model.Holding;
import com.homelab.portfolio.model.HoldingType;
import com.homelab.portfolio.repository.DailySnapshotRepository;
import com.homelab.portfolio.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class QuoteService {

    private static final MathContext MC = MathContext.DECIMAL128;

    private final HoldingRepository holdingRepository;
    private final DailySnapshotRepository snapshotRepository;
    private final QuoteProvider quoteProvider;

    /**
     * Data migration: fixes any existing CASH snapshots that have NULL price.
     * Sets price to BigDecimal.ONE for CASH holdings so that totalValue = sharesOwned * 1 = sharesOwned.
     * This handles legacy snapshots created before the CASH snapshot fix was implemented.
     * Also updates the Holding.lastPrice to 1 for proper display.
     */
    private void migrateNullCashPrices() {
        try {
            List<Holding> cashHoldings = holdingRepository.findAll().stream()
                    .filter(h -> h.getHoldingType() == HoldingType.CASH)
                    .collect(Collectors.toList());

            if (cashHoldings.isEmpty()) {
                return;
            }

            int migratedCount = 0;
            for (Holding cashHolding : cashHoldings) {
                // Get all snapshots for this CASH holding
                List<DailySnapshot> snapshots = snapshotRepository.findByHoldingOrderBySnapshotDateDesc(cashHolding);

                for (DailySnapshot snap : snapshots) {
                    // If price is NULL, set it to 1
                    if (snap.getPrice() == null) {
                        snap.setPrice(BigDecimal.ONE);
                        snapshotRepository.save(snap);
                        migratedCount++;
                    }
                }

                // Also ensure the Holding itself has lastPrice set to 1
                if (cashHolding.getLastPrice() == null) {
                    cashHolding.setLastPrice(BigDecimal.ONE);
                    holdingRepository.save(cashHolding);
                }
            }

            if (migratedCount > 0) {
                log.info("Migrated {} CASH snapshots: set NULL prices to 1.0", migratedCount);
            }
        } catch (Exception e) {
            log.error("Error during CASH price migration: {}", e.getMessage(), e);
            // Don't fail the entire quote refresh if migration has issues
        }
    }

    /**
     * Fetch quotes for all unique tickers and persist a DailySnapshot for each holding.
     * Optimized to fetch each unique ticker only once, even if it appears across multiple portfolios.
     * Safe to call multiple times on the same day — updates the snapshot if it already exists.
     * 
     * Skips CASH holdings (they don't have real quotes).
     * Fetches quotes for ALLOCATED and UNTRACKED holdings.
     * 
     * Also performs data migration: fixes any existing CASH snapshots with NULL prices
     * by setting them to BigDecimal.ONE (so valueTotal = sharesOwned).
     *
     * @return number of holdings successfully updated
     */
    @Transactional
    public int fetchAndStoreQuotes() {
        // Data migration: fix any existing CASH snapshots with NULL price
        migrateNullCashPrices();
        
        List<Holding> allHoldings = holdingRepository.findAll();
        LocalDate today = LocalDate.now();
        
        if (allHoldings.isEmpty()) {
            log.info("No holdings found");
            return 0;
        }

        // Filter out CASH holdings - they don't have real price quotes
        List<Holding> holdings = allHoldings.stream()
                .filter(h -> h.getHoldingType() != HoldingType.CASH)
                .collect(Collectors.toList());

        if (holdings.isEmpty()) {
            log.info("No holdings to fetch quotes for (all are CASH)");
            return 0;
        }

        // Get unique tickers and fetch price once per ticker
        Set<String> uniqueTickers = holdings.stream()
                .map(Holding::getTicker)
                .collect(Collectors.toSet());

        Map<String, BigDecimal> tickerPrices = new HashMap<>();
        for (String ticker : uniqueTickers) {
            try {
                BigDecimal price = quoteProvider.fetchPrice(ticker);
                if (price != null) {
                    tickerPrices.put(ticker, price);
                    log.info("Fetched {} → {}", ticker, price);
                } else {
                    log.warn("Skipping {} — no price returned", ticker);
                }
            } catch (Exception e) {
                log.error("Error fetching price for {}: {}", ticker, e.getMessage(), e);
            }
        }

        // Update all holdings with fetched prices
        int successCount = 0;
        for (Holding holding : holdings) {
            BigDecimal price = tickerPrices.get(holding.getTicker());
            if (price == null) {
                continue; // Price not fetched for this ticker
            }

            try {
                BigDecimal totalValue = price.multiply(holding.getSharesOwned(), MC);

                // Upsert: update today's snapshot if already exists
                Optional<DailySnapshot> existing =
                        snapshotRepository.findByHoldingAndSnapshotDate(holding, today);

                if (existing.isPresent()) {
                    DailySnapshot snap = existing.get();
                    snap.setPrice(price);
                    snap.setTotalValue(totalValue);
                    snapshotRepository.save(snap);
                } else {
                    snapshotRepository.save(DailySnapshot.builder()
                            .holding(holding)
                            .snapshotDate(today)
                            .price(price)
                            .totalValue(totalValue)
                            .build());
                }

                // Cache the latest price on the Holding itself for fast display
                holding.setLastPrice(price);
                holdingRepository.save(holding);

                successCount++;

            } catch (Exception e) {
                log.error("Error processing holding {} ({}): {}", 
                    holding.getTicker(), holding.getId(), e.getMessage(), e);
            }
        }

        // Handle CASH holdings - they don't have prices, but need snapshots too
        // For CASH, totalValue = sharesOwned (since price is effectively 1)
        List<Holding> cashHoldings = allHoldings.stream()
                .filter(h -> h.getHoldingType() == HoldingType.CASH)
                .collect(Collectors.toList());

        for (Holding cashHolding : cashHoldings) {
            try {
                BigDecimal cashValue = cashHolding.getSharesOwned();

                // Upsert: update today's snapshot if already exists
                Optional<DailySnapshot> existing =
                        snapshotRepository.findByHoldingAndSnapshotDate(cashHolding, today);

                if (existing.isPresent()) {
                    DailySnapshot snap = existing.get();
                    snap.setPrice(BigDecimal.ONE);
                    snap.setTotalValue(cashValue);
                    snapshotRepository.save(snap);
                } else {
                    snapshotRepository.save(DailySnapshot.builder()
                            .holding(cashHolding)
                            .snapshotDate(today)
                            .price(BigDecimal.ONE)
                            .totalValue(cashValue)
                            .build());
                }

                // Cache the latest price on the Holding itself for fast display (for CASH, price is 1)
                cashHolding.setLastPrice(BigDecimal.ONE);
                holdingRepository.save(cashHolding);

                successCount++;

            } catch (Exception e) {
                log.error("Error processing CASH holding {} ({}): {}", 
                    cashHolding.getTicker(), cashHolding.getId(), e.getMessage(), e);
            }
        }

        log.info("Quote refresh complete: {}/{} unique tickers fetched, {}/{} holdings updated " +
                 "({} holdings including {} CASH)", 
            tickerPrices.size(), uniqueTickers.size(), successCount, allHoldings.size(), 
            successCount, cashHoldings.size());
        return successCount;
    }
}
