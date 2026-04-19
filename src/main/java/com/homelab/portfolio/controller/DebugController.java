package com.homelab.portfolio.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Controller
public class DebugController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/debug/tables")
    public String debugTables(
            @RequestParam(defaultValue = "portfolio") String table,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        int pageSize = Math.max(size, 5);
        int offset = page * pageSize;

        Page<?> data = switch (table) {
            case "portfolio" -> getPortfolioPage(offset, pageSize, page);
            case "holding" -> getHoldingPage(offset, pageSize, page);
            case "daily_snapshot" -> getDailySnapshotPage(offset, pageSize, page);
            default -> new PageImpl<>(List.of(), PageRequest.of(page, pageSize), 0);
        };

        model.addAttribute("table", table);
        model.addAttribute("data", data);
        model.addAttribute("page", page);
        model.addAttribute("size", pageSize);

        // Add table list for navigation
        model.addAttribute("tables", List.of("portfolio", "holding", "daily_snapshot"));

        return "debug-tables";
    }

    private Page<Map<String, Object>> getPortfolioPage(int offset, int pageSize, int page) {
        String countQuery = "SELECT COUNT(*) FROM PORTFOLIO";
        long total = jdbcTemplate.queryForObject(countQuery, Long.class);

        String dataQuery = "SELECT ID, NAME, DESCRIPTION, DISPLAY_ORDER FROM PORTFOLIO ORDER BY ID OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(dataQuery, offset, pageSize);

        return new PageImpl<>(rows, PageRequest.of(page, pageSize), total);
    }

    private Page<Map<String, Object>> getHoldingPage(int offset, int pageSize, int page) {
        String countQuery = "SELECT COUNT(*) FROM HOLDING";
        long total = jdbcTemplate.queryForObject(countQuery, Long.class);

        String dataQuery = "SELECT ID, PORTFOLIO_ID, TICKER, NAME, SHARES_OWNED, LAST_PRICE, TARGET_ALLOCATION_PCT FROM HOLDING ORDER BY ID OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(dataQuery, offset, pageSize);

        return new PageImpl<>(rows, PageRequest.of(page, pageSize), total);
    }

    private Page<Map<String, Object>> getDailySnapshotPage(int offset, int pageSize, int page) {
        String countQuery = "SELECT COUNT(*) FROM DAILY_SNAPSHOT";
        long total = jdbcTemplate.queryForObject(countQuery, Long.class);

        String dataQuery = "SELECT ID, HOLDING_ID, SNAPSHOT_DATE, PRICE, TOTAL_VALUE FROM DAILY_SNAPSHOT ORDER BY SNAPSHOT_DATE DESC, ID DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(dataQuery, offset, pageSize);

        return new PageImpl<>(rows, PageRequest.of(page, pageSize), total);
    }

    /**
     * Debug view to see snapshots by portfolio and holding
     * Shows which holdings have snapshots for which dates
     */
    @GetMapping("/debug/portfolio-snapshots")
    public String debugPortfolioSnapshots(
            @RequestParam(required = false) Long portfolioId,
            Model model) {

        // Get all portfolios for dropdown
        String portfolioList = "SELECT ID, NAME FROM PORTFOLIO ORDER BY DISPLAY_ORDER";
        List<Map<String, Object>> portfolios = jdbcTemplate.queryForList(portfolioList);

        // If no portfolioId provided or invalid, use first portfolio
        if (portfolioId == null || portfolioId <= 0) {
            if (!portfolios.isEmpty()) {
                portfolioId = ((Number) portfolios.get(0).get("ID")).longValue();
            } else {
                // No portfolios exist
                model.addAttribute("portfolioId", 0);
                model.addAttribute("portfolioName", "No portfolios found");
                model.addAttribute("portfolios", List.of());
                model.addAttribute("snapshots", List.of());
                model.addAttribute("holdings", List.of());
                model.addAttribute("dates", List.of());
                return "debug-portfolio-snapshots";
            }
        }

        // Get portfolio name
        String portfolioName = "Unknown Portfolio";
        try {
            portfolioName = (String) jdbcTemplate.queryForObject(
                    "SELECT NAME FROM PORTFOLIO WHERE ID = ?",
                    new Object[]{portfolioId},
                    String.class
            );
        } catch (Exception e) {
            // Portfolio may have been deleted, use first portfolio
            if (!portfolios.isEmpty()) {
                portfolioId = ((Number) portfolios.get(0).get("ID")).longValue();
                portfolioName = (String) portfolios.get(0).get("NAME");
            }
        }

        final Long finalPortfolioId = portfolioId;

        // Get detailed snapshot info: portfolio -> holding -> dates with snapshots
        String snapshotQuery = """
                SELECT 
                    ds.SNAPSHOT_DATE,
                    h.ID as HOLDING_ID,
                    h.TICKER,
                    h.SHARES_OWNED,
                    ds.PRICE,
                    ds.TOTAL_VALUE,
                    COUNT(*) OVER (PARTITION BY h.ID) as total_snapshots_for_holding,
                    COUNT(*) OVER (PARTITION BY ds.SNAPSHOT_DATE) as total_holdings_on_date
                FROM DAILY_SNAPSHOT ds
                JOIN HOLDING h ON ds.HOLDING_ID = h.ID
                WHERE h.PORTFOLIO_ID = ?
                ORDER BY ds.SNAPSHOT_DATE DESC, h.TICKER ASC
                """;
        List<Map<String, Object>> snapshots = jdbcTemplate.queryForList(snapshotQuery, finalPortfolioId);

        // Get holdings in this portfolio
        String holdingQuery = "SELECT ID, TICKER, NAME, SHARES_OWNED FROM HOLDING WHERE PORTFOLIO_ID = ? ORDER BY TICKER";
        List<Map<String, Object>> holdings = jdbcTemplate.queryForList(holdingQuery, finalPortfolioId);

        // Get distinct dates for this portfolio
        String dateQuery = """
                SELECT DISTINCT ds.SNAPSHOT_DATE
                FROM DAILY_SNAPSHOT ds
                JOIN HOLDING h ON ds.HOLDING_ID = h.ID
                WHERE h.PORTFOLIO_ID = ?
                ORDER BY ds.SNAPSHOT_DATE DESC
                """;
        List<Map<String, Object>> dates = jdbcTemplate.queryForList(dateQuery, finalPortfolioId);

        model.addAttribute("portfolioId", portfolioId);
        model.addAttribute("portfolioName", portfolioName);
        model.addAttribute("portfolios", portfolios);
        model.addAttribute("snapshots", snapshots);
        model.addAttribute("holdings", holdings);
        model.addAttribute("dates", dates);

        return "debug-portfolio-snapshots";
    }
}
