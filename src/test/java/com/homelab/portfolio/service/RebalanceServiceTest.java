package com.homelab.portfolio.service;

import com.homelab.portfolio.dto.DashboardViewModel;
import com.homelab.portfolio.dto.HoldingRow;
import com.homelab.portfolio.model.Holding;
import com.homelab.portfolio.model.HoldingType;
import com.homelab.portfolio.dto.PortfolioHistoryViewModel;
import com.homelab.portfolio.model.DailySnapshot;
import com.homelab.portfolio.model.Portfolio;
import com.homelab.portfolio.model.PortfolioDailySnapshot;
import com.homelab.portfolio.repository.DailySnapshotRepository;
import com.homelab.portfolio.repository.HoldingRepository;
import com.homelab.portfolio.repository.PortfolioDailySnapshotRepository;
import com.homelab.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RebalanceServiceTest {

    @Mock HoldingRepository holdingRepository;
    @Mock DailySnapshotRepository snapshotRepository;
    @Mock PortfolioRepository portfolioRepository;
    @Mock PortfolioDailySnapshotRepository portfolioDailySnapshotRepository;

    @InjectMocks RebalanceService rebalanceService;


    @Test
    void driftIsZeroWhenAllocationMatchesTarget() {
        // 2 holdings with equal shares and equal target — drift should be 0
        Holding h1 = holding("AAPL", "10", "50.0", "100.00");
        Holding h2 = holding("GOOG", "5",  "50.0", "200.00");

        when(holdingRepository.findAll()).thenReturn(List.of(h1, h2));
        when(snapshotRepository.findBySnapshotDateOrderByHoldingTicker(any())).thenReturn(List.of());
        when(snapshotRepository.findByHoldingAndSnapshotDate(any(), any())).thenReturn(Optional.empty());

        DashboardViewModel vm = rebalanceService.buildDashboard();

        assertThat(vm.getTotalValue()).isEqualByComparingTo("2000.00"); // 10×100 + 5×200
        for (HoldingRow row : vm.getHoldings()) {
            assertThat(row.getDriftPct()).isEqualByComparingTo("0.00");
            assertThat(row.getDriftDollars()).isEqualByComparingTo("0.00");
        }
    }

    @Test
    void overweightHoldingHasPositiveDrift() {
        // Target 50/50 but AAPL is worth 70% of the portfolio
        Holding h1 = holding("AAPL", "14", "50.0", "100.00"); // $1400 = 70%
        Holding h2 = holding("GOOG", "3",  "50.0", "200.00"); // $600  = 30%

        when(holdingRepository.findAll()).thenReturn(List.of(h1, h2));
        when(snapshotRepository.findBySnapshotDateOrderByHoldingTicker(any())).thenReturn(List.of());
        when(snapshotRepository.findByHoldingAndSnapshotDate(any(), any())).thenReturn(Optional.empty());

        DashboardViewModel vm = rebalanceService.buildDashboard();

        HoldingRow appleRow = vm.getHoldings().stream()
                .filter(r -> r.getTicker().equals("AAPL")).findFirst().orElseThrow();
        HoldingRow googRow = vm.getHoldings().stream()
                .filter(r -> r.getTicker().equals("GOOG")).findFirst().orElseThrow();

        // AAPL: current 70%, target 50% → drift +20%
        assertThat(appleRow.getDriftPct()).isEqualByComparingTo("20.00");
        // GOOG: current 30%, target 50% → drift -20%
        assertThat(googRow.getDriftPct()).isEqualByComparingTo("-20.00");
    }

    @Test
    void emptyPortfolioReturnsZeroTotal() {
        when(holdingRepository.findAll()).thenReturn(List.of());

        DashboardViewModel vm = rebalanceService.buildDashboard();

        assertThat(vm.getTotalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(vm.getHoldings()).isEmpty();
    }

    @Test
    void buildAllPortfoliosHistoryReturnsPreCalculatedSnapshots() {
        LocalDate today = LocalDate.now();
        PortfolioDailySnapshot snap = PortfolioDailySnapshot.builder()
                .portfolio(null)
                .snapshotDate(today)
                .allocatedValue(new BigDecimal("1000.00"))
                .untrackedValue(new BigDecimal("200.00"))
                .cashValue(new BigDecimal("300.00"))
                .totalValue(new BigDecimal("1500.00"))
                .build();

        when(portfolioDailySnapshotRepository.findByPortfolioIsNullOrderBySnapshotDateAsc())
                .thenReturn(List.of(snap));

        PortfolioHistoryViewModel vm = rebalanceService.buildAllPortfoliosHistory();

        assertThat(vm.getPortfolioName()).isEqualTo("All Portfolios");
        assertThat(vm.getPortfolioId()).isNull();
        assertThat(vm.getHistory()).hasSize(1);
        assertThat(vm.getHistory().get(0).getTotalValue()).isEqualByComparingTo("1500.00");
        assertThat(vm.getHistory().get(0).getAllocatedValue()).isEqualByComparingTo("1000.00");
    }

    @Test
    void recalculateAggregatedSnapshotsProcessesHistoricalDates() {
        LocalDate date = LocalDate.of(2026, 8, 1);
        Portfolio p1 = Portfolio.builder().id(1L).name("Portfolio 1").build();
        Holding h1 = Holding.builder().id(10L).ticker("AAPL").holdingType(HoldingType.ALLOCATED).portfolio(p1).build();
        DailySnapshot snap = DailySnapshot.builder().id(100L).holding(h1).snapshotDate(date).price(new BigDecimal("150.00")).totalValue(new BigDecimal("1500.00")).build();

        when(snapshotRepository.findDistinctSnapshotDates()).thenReturn(List.of(date));
        when(portfolioRepository.findAll()).thenReturn(List.of(p1));
        when(snapshotRepository.findBySnapshotDateOrderByHoldingTicker(date)).thenReturn(List.of(snap));

        int datesProcessed = rebalanceService.recalculateAggregatedSnapshots();

        assertThat(datesProcessed).isEqualTo(1);
        verify(portfolioDailySnapshotRepository, times(2)).save(any(PortfolioDailySnapshot.class)); // 1 for portfolio 1, 1 for grand total
    }


    // ── helpers ──────────────────────────────────────────────────────────────

    private static Holding holding(String ticker, String shares, String targetPct, String lastPrice) {
        return Holding.builder()
                .id((long) ticker.hashCode())
                .ticker(ticker)
                .name(ticker)
                .holdingType(HoldingType.ALLOCATED)
                .sharesOwned(new BigDecimal(shares))
                .targetAllocationPct(new BigDecimal(targetPct))
                .lastPrice(new BigDecimal(lastPrice))
                .build();
    }
}
