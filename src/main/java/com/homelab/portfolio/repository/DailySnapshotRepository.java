package com.homelab.portfolio.repository;

import com.homelab.portfolio.model.DailySnapshot;
import com.homelab.portfolio.model.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailySnapshotRepository extends JpaRepository<DailySnapshot, Long> {

    Optional<DailySnapshot> findByHoldingAndSnapshotDate(Holding holding, LocalDate date);

    List<DailySnapshot> findBySnapshotDateOrderByHoldingTicker(LocalDate date);

    /** Get the N most recent snapshots for a holding — useful for a mini sparkline. */
    List<DailySnapshot> findTop30ByHoldingOrderBySnapshotDateDesc(Holding holding);

    /** Get ALL snapshots for a holding, ordered by date descending — useful for complete history. */
    List<DailySnapshot> findByHoldingOrderBySnapshotDateDesc(Holding holding);

    /** Get the most recent snapshot date for a portfolio (max date across all holdings). */
    @Query("""
            SELECT MAX(ds.snapshotDate)
            FROM DailySnapshot ds
            JOIN ds.holding h
            WHERE h.portfolio.id = :portfolioId
            """)
    Optional<LocalDate> findLatestSnapshotDateForPortfolio(@Param("portfolioId") Long portfolioId);
}
