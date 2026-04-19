package com.homelab.portfolio.config;

import com.homelab.portfolio.model.Holding;
import com.homelab.portfolio.model.Portfolio;
import com.homelab.portfolio.repository.HoldingRepository;
import com.homelab.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds a few example portfolios and holdings when the database is empty.
 * Delete this class (or the holdings via the UI) once you've added your own.
 */
@Component
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final HoldingRepository holdingRepository;
    private final PortfolioRepository portfolioRepository;

    public DataInitializer(HoldingRepository holdingRepository, PortfolioRepository portfolioRepository) {
        this.holdingRepository = holdingRepository;
        this.portfolioRepository = portfolioRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Only initialize if database is empty — preserve all existing data
        if (portfolioRepository.count() > 0) {
            System.out.println("Database already initialized — skipping DataInitializer");
            return;
        }

        log.info("Initializing database with default portfolio and example holdings...");

        // Create default portfolio
        Portfolio defaultPortfolio = Portfolio.builder()
                .name("Main Portfolio")
                .description("Main investment portfolio")
                .displayOrder(0)
                .build();
        defaultPortfolio = portfolioRepository.save(defaultPortfolio);
        log.info("Created default portfolio: Main Portfolio");

        // Seed example data
        log.info("Seeding example portfolio with holdings...");
        List<Holding> examples = List.of(
            Holding.builder()
                .portfolio(defaultPortfolio)
                .ticker("VOO")
                .name("Vanguard S&P 500 ETF")
                .sharesOwned(new BigDecimal("10.0"))
                .targetAllocationPct(new BigDecimal("50.0"))
                .build(),
            Holding.builder()
                .portfolio(defaultPortfolio)
                .ticker("VXUS")
                .name("Vanguard Total International ETF")
                .sharesOwned(new BigDecimal("20.0"))
                .targetAllocationPct(new BigDecimal("30.0"))
                .build(),
            Holding.builder()
                .portfolio(defaultPortfolio)
                .ticker("BND")
                .name("Vanguard Total Bond Market ETF")
                .sharesOwned(new BigDecimal("15.0"))
                .targetAllocationPct(new BigDecimal("20.0"))
                .build()
        );
        holdingRepository.saveAll(examples);
        log.info("Seeded {} example holdings.", examples.size());
    }
}
