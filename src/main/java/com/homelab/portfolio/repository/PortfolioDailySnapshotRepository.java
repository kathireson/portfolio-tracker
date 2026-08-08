package com.homelab.portfolio.repository;

import com.homelab.portfolio.model.Portfolio;
import com.homelab.portfolio.model.PortfolioDailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioDailySnapshotRepository extends JpaRepository<PortfolioDailySnapshot, Long> {

    Optional<PortfolioDailySnapshot> findByPortfolioAndSnapshotDate(Portfolio portfolio, LocalDate snapshotDate);

    Optional<PortfolioDailySnapshot> findByPortfolioIsNullAndSnapshotDate(LocalDate snapshotDate);

    List<PortfolioDailySnapshot> findByPortfolioOrderBySnapshotDateAsc(Portfolio portfolio);

    List<PortfolioDailySnapshot> findByPortfolioIsNullOrderBySnapshotDateAsc();

    void deleteByPortfolioAndSnapshotDate(Portfolio portfolio, LocalDate snapshotDate);

    void deleteByPortfolioIsNullAndSnapshotDate(LocalDate snapshotDate);
}
