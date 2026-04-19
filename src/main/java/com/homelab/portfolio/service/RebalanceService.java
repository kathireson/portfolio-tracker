package com.homelab.portfolio.service;

import com.homelab.portfolio.dto.DashboardViewModel;
import com.homelab.portfolio.dto.HoldingRow;
import com.homelab.portfolio.dto.MultiPortfolioDashboardViewModel;
import com.homelab.portfolio.dto.PortfolioHistoryEntry;
import com.homelab.portfolio.dto.PortfolioHistoryViewModel;
import com.homelab.portfolio.model.DailySnapshot;
import com.homelab.portfolio.model.Holding;
import com.homelab.portfolio.model.HoldingType;
import com.homelab.portfolio.model.Portfolio;
import com.homelab.portfolio.repository.DailySnapshotRepository;
import com.homelab.portfolio.repository.HoldingRepository;
import com.homelab.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RebalanceService {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final HoldingRepository holdingRepository;
    private final DailySnapshotRepository snapshotRepository;
    private final PortfolioRepository portfolioRepository;

    /**
     * Builds the multi-portfolio dashboard view model containing all portfolios.
     */
    @Transactional(readOnly = true)
    public MultiPortfolioDashboardViewModel buildMultiPortfolioDashboard() {
        List<Portfolio> portfolios = portfolioRepository.findAllByOrderByDisplayOrderAsc();
        
        List<DashboardViewModel> portfolioDashboards = portfolios.stream()
                .map(this::buildDashboardForPortfolio)
                .collect(Collectors.toList());

        BigDecimal grandTotal = portfolioDashboards.stream()
                .map(DashboardViewModel::getTotalValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime latestUpdate = portfolioDashboards.stream()
                .map(DashboardViewModel::getLastUpdated)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(LocalDateTime.now());

        return MultiPortfolioDashboardViewModel.builder()
                .portfolios(portfolioDashboards)
                .grandTotalValue(grandTotal.setScale(2, RoundingMode.HALF_UP))
                .lastUpdated(latestUpdate)
                .build();
    }

    /**
     * Builds the dashboard for a specific portfolio.
     * Separates holdings into three categories:
     * - ALLOCATED: tracked for rebalancing (with allocation % and drift)
     * - UNTRACKED: quoted but not tracked for allocation % (no drift)
     * - CASH: no quotes, just value display
     */
    @Transactional(readOnly = true)
    public DashboardViewModel buildDashboardForPortfolio(Portfolio portfolio) {
        List<Holding> holdings = holdingRepository.findByPortfolio(portfolio);
        if (holdings.isEmpty()) {
            return DashboardViewModel.builder()
                    .portfolioId(portfolio.getId())
                    .portfolioName(portfolio.getName())
                    .holdings(List.of())
                    .cashHoldings(List.of())
                    .untrackedHoldings(List.of())
                    .allocatedValue(BigDecimal.ZERO)
                    .untrackedValue(BigDecimal.ZERO)
                    .cashValue(BigDecimal.ZERO)
                    .totalValue(BigDecimal.ZERO)
                    .totalTargetPct(BigDecimal.ZERO)
                    .snapshotDate(LocalDate.now())
                    .quotesLoadedToday(false)
                    .build();
        }

        final LocalDate today = LocalDate.now();
        List<DailySnapshot> todaySnapshots = snapshotRepository.findBySnapshotDateOrderByHoldingTicker(today);
        boolean quotesLoadedToday = !todaySnapshots.isEmpty();

        // Resolve current price per holding
        List<HoldingPriceEntry> entries = holdings.stream().map(h -> {
            BigDecimal price = resolvePrice(h, today);
            // For CASH holdings, sharesOwned stores the cash amount directly; for others, value = price * shares
            BigDecimal value;
            if (h.getHoldingType() == HoldingType.CASH) {
                value = h.getSharesOwned();  // sharesOwned IS the cash amount for CASH holdings
                price = BigDecimal.ONE;  // Set price to 1 for display consistency
            } else {
                value = price == null ? BigDecimal.ZERO : price.multiply(h.getSharesOwned(), MC);
            }
            return new HoldingPriceEntry(h, price, value);
        }).toList();

        // Separate holdings by type
        List<HoldingPriceEntry> allocatedEntries = entries.stream()
                .filter(e -> e.holding().getHoldingType() == HoldingType.ALLOCATED)
                .collect(Collectors.toList());
        List<HoldingPriceEntry> untrackedEntries = entries.stream()
                .filter(e -> e.holding().getHoldingType() == HoldingType.UNTRACKED)
                .collect(Collectors.toList());
        List<HoldingPriceEntry> cashEntries = entries.stream()
                .filter(e -> e.holding().getHoldingType() == HoldingType.CASH)
                .collect(Collectors.toList());

        BigDecimal allocatedValue = allocatedEntries.stream()
                .map(HoldingPriceEntry::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal untrackedValue = untrackedEntries.stream()
                .map(HoldingPriceEntry::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cashValue = cashEntries.stream()
                .map(HoldingPriceEntry::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalValue = allocatedValue.add(untrackedValue).add(cashValue);

        BigDecimal totalTargetPct = allocatedEntries.stream()
                .map(e -> e.holding().getTargetAllocationPct())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Build rows, using only allocatedValue for allocation % calculations
        List<HoldingRow> allocatedRows = allocatedEntries.stream()
                .map(e -> buildRow(e, allocatedValue, HoldingType.ALLOCATED))
                .sorted(Comparator.comparing(HoldingRow::getCurrentValue).reversed())
                .collect(Collectors.toList());

        List<HoldingRow> untrackedRows = untrackedEntries.stream()
                .map(e -> buildRow(e, untrackedValue, HoldingType.UNTRACKED))
                .sorted(Comparator.comparing(HoldingRow::getCurrentValue).reversed())
                .collect(Collectors.toList());

        List<HoldingRow> cashRows = cashEntries.stream()
                .map(e -> buildRow(e, cashValue, HoldingType.CASH))
                .sorted(Comparator.comparing(HoldingRow::getCurrentValue).reversed())
                .collect(Collectors.toList());

        // Get actual last quote fetch time from the most recent snapshot date
        LocalDateTime lastQuoteFetchTime = snapshotRepository.findLatestSnapshotDateForPortfolio(portfolio.getId())
                .map(date -> date.equals(today) ? LocalDateTime.now() : date.atTime(9, 35))
                .orElse(null);

        return DashboardViewModel.builder()
                .portfolioId(portfolio.getId())
                .portfolioName(portfolio.getName())
                .totalValue(totalValue.setScale(2, RoundingMode.HALF_UP))
                .allocatedValue(allocatedValue.setScale(2, RoundingMode.HALF_UP))
                .untrackedValue(untrackedValue.setScale(2, RoundingMode.HALF_UP))
                .cashValue(cashValue.setScale(2, RoundingMode.HALF_UP))
                .totalTargetPct(totalTargetPct.setScale(2, RoundingMode.HALF_UP))
                .snapshotDate(today)
                .lastUpdated(lastQuoteFetchTime)
                .holdings(allocatedRows)
                .cashHoldings(cashRows)
                .untrackedHoldings(untrackedRows)
                .quotesLoadedToday(quotesLoadedToday)
                .build();
    }

    /**
     * Builds the full dashboard view model using either today's snapshots
     * (if already loaded) or the most recent snapshot date available, falling
     * back to lastPrice on the Holding entity if no snapshots exist.
     * @deprecated use buildMultiPortfolioDashboard() or buildDashboardForPortfolio() instead
     */
    @Deprecated
    @Transactional(readOnly = true)
    public DashboardViewModel buildDashboard() {
        List<Holding> holdings = holdingRepository.findAll();
        if (holdings.isEmpty()) {
            return DashboardViewModel.builder()
                    .holdings(List.of())
                    .cashHoldings(List.of())
                    .untrackedHoldings(List.of())
                    .totalValue(BigDecimal.ZERO)
                    .allocatedValue(BigDecimal.ZERO)
                    .untrackedValue(BigDecimal.ZERO)
                    .cashValue(BigDecimal.ZERO)
                    .totalTargetPct(BigDecimal.ZERO)
                    .snapshotDate(LocalDate.now())
                    .quotesLoadedToday(false)
                    .build();
        }

        LocalDate today = LocalDate.now();
        List<DailySnapshot> todaySnapshots = snapshotRepository.findBySnapshotDateOrderByHoldingTicker(today);
        boolean quotesLoadedToday = !todaySnapshots.isEmpty();

        // Resolve current price per holding
        List<HoldingPriceEntry> entries = holdings.stream().map(h -> {
            BigDecimal price = resolvePrice(h, today);
            // For CASH holdings, sharesOwned stores the cash amount directly; for others, value = price * shares
            BigDecimal value;
            if (h.getHoldingType() == HoldingType.CASH) {
                value = h.getSharesOwned();  // sharesOwned IS the cash amount for CASH holdings
                price = BigDecimal.ONE;  // Set price to 1 for display consistency
            } else {
                value = price == null ? BigDecimal.ZERO : price.multiply(h.getSharesOwned(), MC);
            }
            return new HoldingPriceEntry(h, price, value);
        }).toList();

        // Separate holdings by type
        List<HoldingPriceEntry> allocatedEntries = entries.stream()
                .filter(e -> e.holding().getHoldingType() == HoldingType.ALLOCATED)
                .collect(Collectors.toList());
        List<HoldingPriceEntry> untrackedEntries = entries.stream()
                .filter(e -> e.holding().getHoldingType() == HoldingType.UNTRACKED)
                .collect(Collectors.toList());
        List<HoldingPriceEntry> cashEntries = entries.stream()
                .filter(e -> e.holding().getHoldingType() == HoldingType.CASH)
                .collect(Collectors.toList());

        BigDecimal allocatedValue = allocatedEntries.stream()
                .map(HoldingPriceEntry::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal untrackedValue = untrackedEntries.stream()
                .map(HoldingPriceEntry::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cashValue = cashEntries.stream()
                .map(HoldingPriceEntry::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalValue = allocatedValue.add(untrackedValue).add(cashValue);

        BigDecimal totalTargetPct = allocatedEntries.stream()
                .map(e -> e.holding().getTargetAllocationPct())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<HoldingRow> allocatedRows = allocatedEntries.stream()
                .map(e -> buildRow(e, allocatedValue, HoldingType.ALLOCATED))
                .sorted(Comparator.comparing(HoldingRow::getCurrentValue).reversed())
                .collect(Collectors.toList());

        List<HoldingRow> untrackedRows = untrackedEntries.stream()
                .map(e -> buildRow(e, untrackedValue, HoldingType.UNTRACKED))
                .sorted(Comparator.comparing(HoldingRow::getCurrentValue).reversed())
                .collect(Collectors.toList());

        List<HoldingRow> cashRows = cashEntries.stream()
                .map(e -> buildRow(e, cashValue, HoldingType.CASH))
                .sorted(Comparator.comparing(HoldingRow::getCurrentValue).reversed())
                .collect(Collectors.toList());

        return DashboardViewModel.builder()
                .totalValue(totalValue.setScale(2, RoundingMode.HALF_UP))
                .allocatedValue(allocatedValue.setScale(2, RoundingMode.HALF_UP))
                .untrackedValue(untrackedValue.setScale(2, RoundingMode.HALF_UP))
                .cashValue(cashValue.setScale(2, RoundingMode.HALF_UP))
                .totalTargetPct(totalTargetPct.setScale(2, RoundingMode.HALF_UP))
                .snapshotDate(today)
                .lastUpdated(LocalDateTime.now())
                .holdings(allocatedRows)
                .cashHoldings(cashRows)
                .untrackedHoldings(untrackedRows)
                .quotesLoadedToday(quotesLoadedToday)
                .build();
    }

    private BigDecimal resolvePrice(Holding holding, LocalDate date) {
        Optional<DailySnapshot> snap = snapshotRepository.findByHoldingAndSnapshotDate(holding, date);
        if (snap.isPresent()) return snap.get().getPrice();
        // Fall back to the cached lastPrice on the entity
        return holding.getLastPrice();
    }

    private HoldingRow buildRow(HoldingPriceEntry e, BigDecimal baseValue, HoldingType type) {
        Holding h = e.holding();
        BigDecimal price = e.price() != null ? e.price() : BigDecimal.ZERO;
        BigDecimal value = e.value();

        HoldingRow.HoldingRowBuilder builder = HoldingRow.builder()
                .id(h.getId())
                .ticker(h.getTicker())
                .name(h.getName() != null ? h.getName() : h.getTicker())
                .sharesOwned(h.getSharesOwned())
                .holdingType(type)
                .currentPrice(price.setScale(4, RoundingMode.HALF_UP))
                .currentValue(value.setScale(2, RoundingMode.HALF_UP));

        // Only calculate allocation % and drift for ALLOCATED holdings
        if (type == HoldingType.ALLOCATED) {
            BigDecimal currentAllocPct;
            BigDecimal driftPct;
            BigDecimal driftDollars;

            if (baseValue.compareTo(BigDecimal.ZERO) == 0) {
                currentAllocPct = BigDecimal.ZERO;
                driftPct = h.getTargetAllocationPct().negate();
                driftDollars = BigDecimal.ZERO;
            } else {
                currentAllocPct = value.divide(baseValue, MC).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
                driftPct = currentAllocPct.subtract(h.getTargetAllocationPct()).setScale(2, RoundingMode.HALF_UP);
                driftDollars = driftPct.divide(HUNDRED, MC).multiply(baseValue).setScale(2, RoundingMode.HALF_UP);
            }

            builder.currentAllocationPct(currentAllocPct)
                   .targetAllocationPct(h.getTargetAllocationPct())
                   .driftPct(driftPct)
                   .driftDollars(driftDollars);
        } else {
            // For CASH and UNTRACKED, set allocation fields to zero/null
            builder.currentAllocationPct(BigDecimal.ZERO)
                   .targetAllocationPct(BigDecimal.ZERO)
                   .driftPct(BigDecimal.ZERO)
                   .driftDollars(BigDecimal.ZERO);
        }

        return builder.build();
    }

    /**
     * Builds a portfolio history view model with daily portfolio values.
     * Fetches all daily snapshots for holdings in the portfolio and calculates
     * the total portfolio value for each day, separated by holding type.
     * 
     * @param portfolioId the ID of the portfolio
     * @return PortfolioHistoryViewModel with historical data including allocated/untracked/cash breakdowns
     */
    @Transactional(readOnly = true)
    public PortfolioHistoryViewModel buildPortfolioHistory(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found: " + portfolioId));
        
        List<Holding> holdings = holdingRepository.findByPortfolio(portfolio);
        if (holdings.isEmpty()) {
            return PortfolioHistoryViewModel.builder()
                    .portfolioId(portfolioId)
                    .portfolioName(portfolio.getName())
                    .history(List.of())
                    .build();
        }

        // Get all snapshots for all holdings in this portfolio, grouped by date and type
        java.util.Map<LocalDate, DailyTotalsByType> dailyTotals = new java.util.TreeMap<>();
        
        for (Holding holding : holdings) {
            // Get ALL snapshots for this holding (no limit) to ensure all historical data is included
            List<DailySnapshot> snapshots = snapshotRepository.findByHoldingOrderBySnapshotDateDesc(holding);
            
            for (DailySnapshot snap : snapshots) {
                dailyTotals.computeIfAbsent(snap.getSnapshotDate(), k -> new DailyTotalsByType())
                        .addValue(holding.getHoldingType(), snap.getTotalValue());
            }
        }

        // Convert to sorted list of history entries (oldest first for chart display)
        List<PortfolioHistoryEntry> history = dailyTotals.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(e -> {
                    DailyTotalsByType totals = e.getValue();
                    return PortfolioHistoryEntry.builder()
                            .date(e.getKey())
                            .allocatedValue(totals.getAllocatedValue().setScale(2, RoundingMode.HALF_UP))
                            .untrackedValue(totals.getUntrackedValue().setScale(2, RoundingMode.HALF_UP))
                            .cashValue(totals.getCashValue().setScale(2, RoundingMode.HALF_UP))
                            .totalValue(totals.getTotalValue().setScale(2, RoundingMode.HALF_UP))
                            .build();
                })
                .collect(Collectors.toList());

        return PortfolioHistoryViewModel.builder()
                .portfolioId(portfolioId)
                .portfolioName(portfolio.getName())
                .history(history)
                .build();
    }

    /**
     * Helper class to track daily totals by holding type.
     */
    private static class DailyTotalsByType {
        private BigDecimal allocatedValue = BigDecimal.ZERO;
        private BigDecimal untrackedValue = BigDecimal.ZERO;
        private BigDecimal cashValue = BigDecimal.ZERO;

        void addValue(HoldingType type, BigDecimal value) {
            if (HoldingType.ALLOCATED == type) {
                allocatedValue = allocatedValue.add(value);
            } else if (HoldingType.UNTRACKED == type) {
                untrackedValue = untrackedValue.add(value);
            } else if (HoldingType.CASH == type) {
                cashValue = cashValue.add(value);
            }
        }

        BigDecimal getAllocatedValue() { return allocatedValue; }
        BigDecimal getUntrackedValue() { return untrackedValue; }
        BigDecimal getCashValue() { return cashValue; }
        BigDecimal getTotalValue() { return allocatedValue.add(untrackedValue).add(cashValue); }
    }

    private record HoldingPriceEntry(Holding holding, BigDecimal price, BigDecimal value) {}
}
