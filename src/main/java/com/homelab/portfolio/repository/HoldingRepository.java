package com.homelab.portfolio.repository;

import com.homelab.portfolio.model.Holding;
import com.homelab.portfolio.model.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HoldingRepository extends JpaRepository<Holding, Long> {
    Optional<Holding> findByTickerIgnoreCase(String ticker);

    List<Holding> findByPortfolio(Portfolio portfolio);

    Optional<Holding> findByPortfolioAndTickerIgnoreCase(Portfolio portfolio, String ticker);
}
