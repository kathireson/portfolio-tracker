package com.homelab.portfolio.service;

import com.homelab.portfolio.dto.DashboardViewModel;
import com.homelab.portfolio.dto.HoldingRow;
import com.homelab.portfolio.model.Holding;
import com.homelab.portfolio.model.HoldingType;
import com.homelab.portfolio.repository.DailySnapshotRepository;
import com.homelab.portfolio.repository.HoldingRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RebalanceServiceTest {

    @Mock HoldingRepository holdingRepository;
    @Mock DailySnapshotRepository snapshotRepository;
    @Mock PortfolioRepository portfolioRepository;

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
