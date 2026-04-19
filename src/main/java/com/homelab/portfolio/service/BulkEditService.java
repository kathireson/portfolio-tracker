package com.homelab.portfolio.service;

import com.homelab.portfolio.dto.BulkEditResult;
import com.homelab.portfolio.model.Holding;
import com.homelab.portfolio.model.Portfolio;
import com.homelab.portfolio.repository.HoldingRepository;
import com.homelab.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Service to handle bulk editing of holdings from TSV (tab-separated values) format
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BulkEditService {

    private final HoldingRepository holdingRepository;
    private final PortfolioRepository portfolioRepository;

    /**
     * Parse TSV data and update holdings in the specified portfolio.
     * 
     * Expected TSV format:
     * Symbol | Last Price $ | Change $ | Change % | Qty # | Price Paid $ | Day's Gain $ | Total Gain $ | Total Gain % | Value $
     * 
     * Only Symbol (column 0) and Qty # (column 4) are used.
     * Unknown tickers are skipped.
     * 
     * @param portfolioId The portfolio to update
     * @param tsvData The TSV data as a string
     * @return BulkEditResult with statistics and any errors
     */
    public BulkEditResult processBulkEdit(Long portfolioId, String tsvData) {
        Optional<Portfolio> portfolioOpt = portfolioRepository.findById(portfolioId);
        if (portfolioOpt.isEmpty()) {
            return BulkEditResult.builder()
                    .success(false)
                    .message("Portfolio not found")
                    .updated(0)
                    .skipped(0)
                    .skippedTickers(new ArrayList<>())
                    .build();
        }

        Portfolio portfolio = portfolioOpt.get();
        String[] lines = tsvData.trim().split("\n");
        
        if (lines.length < 2) {
            return BulkEditResult.builder()
                    .success(false)
                    .message("TSV data must contain at least a header row and one data row")
                    .updated(0)
                    .skipped(0)
                    .skippedTickers(new ArrayList<>())
                    .build();
        }

        int updated = 0;
        int skipped = 0;
        List<String> skippedTickers = new ArrayList<>();

        // Process each line starting from index 1 (skip header)
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }

            try {
                String[] parts = line.split("\t");
                
                if (parts.length < 5) {
                    log.warn("Line {} has insufficient columns: {}", i, line);
                    skipped++;
                    continue;
                }

                String symbol = parts[0].trim().toUpperCase();
                String qtyStr = parts[4].trim();

                if (symbol.isEmpty() || qtyStr.isEmpty()) {
                    skipped++;
                    continue;
                }

                // Try to parse quantity, removing any non-numeric characters except decimal point
                BigDecimal quantity = new BigDecimal(qtyStr.replaceAll("[^0-9.]", ""));

                // Find holding by ticker in this portfolio
                Optional<Holding> holdingOpt = holdingRepository
                        .findByPortfolioAndTickerIgnoreCase(portfolio, symbol);

                if (holdingOpt.isPresent()) {
                    Holding holding = holdingOpt.get();
                    holding.setSharesOwned(quantity);
                    holdingRepository.save(holding);
                    updated++;
                    log.info("Updated holding: {} with quantity: {}", symbol, quantity);
                } else {
                    skipped++;
                    skippedTickers.add(symbol);
                    log.warn("Holding not found in portfolio: {}", symbol);
                }
            } catch (NumberFormatException e) {
                skipped++;
                log.warn("Error parsing line {}: {}", i, e.getMessage());
            } catch (Exception e) {
                skipped++;
                log.error("Unexpected error processing line {}: {}", i, e.getMessage());
            }
        }

        String message = String.format("Updated %d holdings. Skipped %d entries.", updated, skipped);
        if (!skippedTickers.isEmpty()) {
            message += " Unknown tickers: " + String.join(", ", skippedTickers);
        }

        return BulkEditResult.builder()
                .success(true)
                .message(message)
                .updated(updated)
                .skipped(skipped)
                .skippedTickers(skippedTickers)
                .build();
    }
}
