package com.homelab.portfolio.service;

import com.homelab.portfolio.dto.RebalanceSuggestorRequest;
import com.homelab.portfolio.dto.RebalanceSuggestorResponse;
import com.homelab.portfolio.model.Holding;
import com.homelab.portfolio.model.HoldingType;
import com.homelab.portfolio.model.Portfolio;
import com.homelab.portfolio.repository.HoldingRepository;
import com.homelab.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RebalanceSuggestorService {

    private final HoldingRepository holdingRepository;
    private final PortfolioRepository portfolioRepository;

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Transactional(readOnly = true)
    public RebalanceSuggestorResponse suggestRebalance(RebalanceSuggestorRequest request) {
        Portfolio portfolio = portfolioRepository.findById(request.getPortfolioId())
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found: " + request.getPortfolioId()));

        List<Holding> allocatedHoldings = holdingRepository.findByPortfolio(portfolio).stream()
                .filter(h -> h.getHoldingType() == HoldingType.ALLOCATED)
                .collect(Collectors.toList());

        if (allocatedHoldings.isEmpty()) {
            return RebalanceSuggestorResponse.builder()
                    .portfolioId(portfolio.getId())
                    .portfolioName(portfolio.getName())
                    .buys(List.of())
                    .sells(List.of())
                    .projectedHoldings(List.of())
                    .expectedInvestedValue(BigDecimal.ZERO)
                    .remainingCash(request.isUseNewMoney() ? request.getNewMoneyAmount() : BigDecimal.ZERO)
                    .build();
        }

        // Parse optional TSV prices
        Map<String, BigDecimal> tsvPrices = parseTsvPrices(request.getTsvPrices());

        // Initial mapping step: Resolve prices and calculate current values
        List<HoldingContext> contextList = new ArrayList<>();
        BigDecimal currentTotalValue = BigDecimal.ZERO;

        for (Holding h : allocatedHoldings) {
            BigDecimal price;
            if (tsvPrices.containsKey(h.getTicker())) {
                price = tsvPrices.get(h.getTicker());
            } else {
                // To fetch price safely using existing method from rebalanceService, actually RebalanceService doesn't expose resolvePrice publicly.
                // We'll read from lastPrice for now since DailySnapshots might be tedious to query manually. 
                // Actually, let's just use h.getLastPrice() safely. If it's old, user should refresh quotes first.
                price = h.getLastPrice() != null ? h.getLastPrice() : BigDecimal.ZERO;
            }

            BigDecimal currentValue = price.multiply(h.getSharesOwned());
            currentTotalValue = currentTotalValue.add(currentValue);

            contextList.add(new HoldingContext(h, price, h.getSharesOwned(), currentValue));
        }

        // Calculate initial drifts to see if we exceed tolerance
        boolean needsRebalance = request.isUseNewMoney() && request.getNewMoneyAmount().compareTo(BigDecimal.ZERO) > 0;
        
        for (HoldingContext ctx : contextList) {
            BigDecimal currentPct = BigDecimal.ZERO;
            if (currentTotalValue.compareTo(BigDecimal.ZERO) > 0) {
                currentPct = ctx.currentValue.divide(currentTotalValue, MC).multiply(HUNDRED);
            }
            BigDecimal drift = currentPct.subtract(ctx.holding.getTargetAllocationPct());
            
            if (drift.abs().compareTo(request.getTolerancePct()) > 0) {
                needsRebalance = true;
            }
        }

        if (!needsRebalance) {
            // Everyone is within tolerance, no suggestions needed
            return buildResponse(portfolio, contextList, BigDecimal.ZERO, currentTotalValue, request.isUseNewMoney() ? request.getNewMoneyAmount() : BigDecimal.ZERO);
        }

        // --- Calculate Targets ---
        BigDecimal targetTotalValue = currentTotalValue;
        BigDecimal newMoney = BigDecimal.ZERO;
        
        if (request.isUseNewMoney() && request.getNewMoneyAmount() != null) {
            newMoney = request.getNewMoneyAmount();
            targetTotalValue = targetTotalValue.add(newMoney);
        }

        // 1. Calculate Exact Target Shares and determine projected shares
        for (HoldingContext ctx : contextList) {
            if (ctx.price.compareTo(BigDecimal.ZERO) == 0) {
                ctx.projectedShares = ctx.currentShares;
                ctx.exactTargetShares = ctx.currentShares;
                continue;
            }
            
            BigDecimal exactTargetValue = targetTotalValue.multiply(ctx.holding.getTargetAllocationPct()).divide(HUNDRED, MC);
            ctx.exactTargetShares = exactTargetValue.divide(ctx.price, MC);
            
            if (request.isAllowFractionalShares()) {
                // Fractional shares allowed: projected shares match exact target
                ctx.projectedShares = ctx.exactTargetShares.setScale(8, RoundingMode.HALF_UP);
            } else {
                // No fractional shares: round the TRADE to whole shares, not the final holding.
                // This preserves existing fractional shares from dividend reinvestment.
                BigDecimal exactTrade = ctx.exactTargetShares.subtract(ctx.currentShares);
                BigDecimal roundedTrade;
                if (exactTrade.compareTo(BigDecimal.ZERO) >= 0) {
                    roundedTrade = exactTrade.setScale(0, RoundingMode.DOWN);  // floor buys
                } else {
                    roundedTrade = exactTrade.setScale(0, RoundingMode.UP);    // floor sells (toward zero)
                }
                ctx.projectedShares = ctx.currentShares.add(roundedTrade);
            }
        }

        // 2. Distribute remaining cash for integer shares algorithm
        BigDecimal allocatedTargetCash = BigDecimal.ZERO;
        for (HoldingContext ctx : contextList) {
            allocatedTargetCash = allocatedTargetCash.add(ctx.projectedShares.multiply(ctx.price));
        }
        
        BigDecimal remainingCash = targetTotalValue.subtract(allocatedTargetCash);

        if (!request.isAllowFractionalShares()) {
            // Sort by how far each holding is below its exact target (largest gap first)
            List<HoldingContext> sortedByRemainder = new ArrayList<>(contextList);
            sortedByRemainder.sort((a, b) -> {
                if (a.price.compareTo(BigDecimal.ZERO) == 0) return 1;
                if (b.price.compareTo(BigDecimal.ZERO) == 0) return -1;
                BigDecimal gapA = a.exactTargetShares.subtract(a.projectedShares);
                BigDecimal gapB = b.exactTargetShares.subtract(b.projectedShares);
                return gapB.compareTo(gapA);
            });
            
            for (HoldingContext ctx : sortedByRemainder) {
                if (ctx.price.compareTo(BigDecimal.ZERO) > 0 && remainingCash.compareTo(ctx.price) >= 0) {
                    // Buy 1 additional whole share to deploy leftover cash
                    ctx.projectedShares = ctx.projectedShares.add(BigDecimal.ONE);
                    remainingCash = remainingCash.subtract(ctx.price);
                }
            }
        }

        // 3. Generate Actions based on Current vs Projected
        List<RebalanceSuggestorResponse.RebalanceAction> buys = new ArrayList<>();
        List<RebalanceSuggestorResponse.RebalanceAction> sells = new ArrayList<>();
        BigDecimal projectedTotalValue = BigDecimal.ZERO;

        for (HoldingContext ctx : contextList) {
            ctx.projectedValue = ctx.projectedShares.multiply(ctx.price);
            projectedTotalValue = projectedTotalValue.add(ctx.projectedValue);
            
            BigDecimal sharesToTrade = ctx.projectedShares.subtract(ctx.currentShares);
            
            // To avoid micro-fractional drift trades, ignore very small trades (e.g. < 0.000001) or < $0.01 value
            BigDecimal tradeValue = sharesToTrade.abs().multiply(ctx.price);
            if (sharesToTrade.abs().compareTo(BigDecimal.valueOf(0.000001)) < 0 || tradeValue.compareTo(BigDecimal.valueOf(0.01)) < 0) {
                continue;
            }

            if (sharesToTrade.compareTo(BigDecimal.ZERO) > 0) {
                buys.add(RebalanceSuggestorResponse.RebalanceAction.builder()
                        .ticker(ctx.holding.getTicker())
                        .actionType("BUY")
                        .shares(sharesToTrade)
                        .estimatedPrice(ctx.price)
                        .totalValue(tradeValue)
                        .build());
            } else {
                sells.add(RebalanceSuggestorResponse.RebalanceAction.builder()
                        .ticker(ctx.holding.getTicker())
                        .actionType("SELL")
                        .shares(sharesToTrade.abs())
                        .estimatedPrice(ctx.price)
                        .totalValue(tradeValue)
                        .build());
            }
        }

        buys.sort(Comparator.comparing(RebalanceSuggestorResponse.RebalanceAction::getTotalValue).reversed());
        sells.sort(Comparator.comparing(RebalanceSuggestorResponse.RebalanceAction::getTotalValue).reversed());

        // 4. Build Projected View
        List<RebalanceSuggestorResponse.ProjectedHoldingRow> projectedRows = new ArrayList<>();
        for (HoldingContext ctx : contextList) {
            BigDecimal projPct = BigDecimal.ZERO;
            if (projectedTotalValue.compareTo(BigDecimal.ZERO) > 0) {
                projPct = ctx.projectedValue.divide(projectedTotalValue, MC).multiply(HUNDRED);
            }
            BigDecimal drift = projPct.subtract(ctx.holding.getTargetAllocationPct());

            projectedRows.add(RebalanceSuggestorResponse.ProjectedHoldingRow.builder()
                    .ticker(ctx.holding.getTicker())
                    .finalShares(ctx.projectedShares.setScale(4, RoundingMode.HALF_UP))
                    .estimatedPrice(ctx.price.setScale(2, RoundingMode.HALF_UP))
                    .finalValue(ctx.projectedValue.setScale(2, RoundingMode.HALF_UP))
                    .targetAllocationPct(ctx.holding.getTargetAllocationPct().setScale(2, RoundingMode.HALF_UP))
                    .projectedAllocationPct(projPct.setScale(2, RoundingMode.HALF_UP))
                    .driftPct(drift.setScale(2, RoundingMode.HALF_UP))
                    .build());
        }

        projectedRows.sort(Comparator.comparing(RebalanceSuggestorResponse.ProjectedHoldingRow::getFinalValue).reversed());

        return RebalanceSuggestorResponse.builder()
                .portfolioId(portfolio.getId())
                .portfolioName(portfolio.getName())
                .buys(buys)
                .sells(sells)
                .projectedHoldings(projectedRows)
                .expectedInvestedValue(projectedTotalValue.setScale(2, RoundingMode.HALF_UP))
                .remainingCash(remainingCash.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    private RebalanceSuggestorResponse buildResponse(Portfolio portfolio, List<HoldingContext> contextList, 
            BigDecimal remainingCash, BigDecimal totalValue, BigDecimal newMoney) {
        
        List<RebalanceSuggestorResponse.ProjectedHoldingRow> projectedRows = new ArrayList<>();
        for(HoldingContext ctx : contextList) {
            BigDecimal projPct = BigDecimal.ZERO;
            if (totalValue.compareTo(BigDecimal.ZERO) > 0) {
                projPct = ctx.currentValue.divide(totalValue, MC).multiply(HUNDRED);
            }
            BigDecimal drift = projPct.subtract(ctx.holding.getTargetAllocationPct());
            projectedRows.add(RebalanceSuggestorResponse.ProjectedHoldingRow.builder()
                    .ticker(ctx.holding.getTicker())
                    .finalShares(ctx.currentShares.setScale(4, RoundingMode.HALF_UP))
                    .estimatedPrice(ctx.price.setScale(2, RoundingMode.HALF_UP))
                    .finalValue(ctx.currentValue.setScale(2, RoundingMode.HALF_UP))
                    .targetAllocationPct(ctx.holding.getTargetAllocationPct().setScale(2, RoundingMode.HALF_UP))
                    .projectedAllocationPct(projPct.setScale(2, RoundingMode.HALF_UP))
                    .driftPct(drift.setScale(2, RoundingMode.HALF_UP))
                    .build());
        }
        
        projectedRows.sort(Comparator.comparing(RebalanceSuggestorResponse.ProjectedHoldingRow::getFinalValue).reversed());

        return RebalanceSuggestorResponse.builder()
                .portfolioId(portfolio.getId())
                .portfolioName(portfolio.getName())
                .buys(List.of())
                .sells(List.of())
                .projectedHoldings(projectedRows)
                .expectedInvestedValue(totalValue.setScale(2, RoundingMode.HALF_UP))
                .remainingCash(remainingCash.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    private Map<String, BigDecimal> parseTsvPrices(String tsv) {
        java.util.Map<String, BigDecimal> map = new java.util.HashMap<>();
        if (tsv == null || tsv.trim().isEmpty()) return map;

        String[] lines = tsv.split("\\r?\\n");
        for (String line : lines) {
            String[] parts = line.split("\\t");
            if (parts.length >= 2) { // Need at least ticker and some price column
                String ticker = parts[0].trim().toUpperCase();
                try {
                    // Try to parse price. Usually in brokerages it's the second column (Last Price $)
                    String priceStr = parts[1].replace("$", "").replace(",", "").trim();
                    BigDecimal price = new BigDecimal(priceStr);
                    map.put(ticker, price);
                } catch (Exception e) {
                    // Ignore rows that don't match format
                    log.debug("Skipping TSV row parsing for: " + line);
                }
            }
        }
        return map;
    }

    private static class HoldingContext {
        Holding holding;
        BigDecimal price;
        BigDecimal currentShares;
        BigDecimal currentValue;
        
        BigDecimal exactTargetShares = BigDecimal.ZERO;
        BigDecimal projectedShares = BigDecimal.ZERO;
        BigDecimal projectedValue = BigDecimal.ZERO;

        public HoldingContext(Holding holding, BigDecimal price, BigDecimal currentShares, BigDecimal currentValue) {
            this.holding = holding;
            this.price = price;
            this.currentShares = currentShares;
            this.currentValue = currentValue;
        }
    }
}
