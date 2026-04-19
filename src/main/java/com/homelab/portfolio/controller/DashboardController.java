package com.homelab.portfolio.controller;

import com.homelab.portfolio.dto.DashboardViewModel;
import com.homelab.portfolio.dto.MultiPortfolioDashboardViewModel;
import com.homelab.portfolio.dto.PortfolioHistoryViewModel;
import com.homelab.portfolio.model.Holding;
import com.homelab.portfolio.model.HoldingType;
import com.homelab.portfolio.model.Portfolio;
import com.homelab.portfolio.repository.HoldingRepository;
import com.homelab.portfolio.repository.PortfolioRepository;
import com.homelab.portfolio.service.QuoteService;
import com.homelab.portfolio.service.RebalanceService;
import com.homelab.portfolio.dto.BulkEditRequest;
import com.homelab.portfolio.dto.BulkEditResult;
import com.homelab.portfolio.service.BulkEditService;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@Slf4j
@RequiredArgsConstructor
public class DashboardController {

    private final RebalanceService rebalanceService;
    private final QuoteService quoteService;
    private final HoldingRepository holdingRepository;
    private final PortfolioRepository portfolioRepository;
    private final BulkEditService bulkEditService;

    /** Main dashboard */
    @GetMapping({"", "/"})
    public String dashboard(Model model) {
        MultiPortfolioDashboardViewModel vm = rebalanceService.buildMultiPortfolioDashboard();
        model.addAttribute("vm", vm);
        
        // Get first portfolio for the add form (or create default if none exists)
        Portfolio defaultPortfolio = portfolioRepository.findAllByOrderByDisplayOrderAsc().stream()
                .findFirst()
                .orElseGet(() -> {
                    Portfolio p = Portfolio.builder()
                        .name("Main Portfolio")
                        .displayOrder(0)
                            .build();
                    return portfolioRepository.save(p);
                });
        
        Holding newHolding = new Holding();
        newHolding.setPortfolio(defaultPortfolio);
        newHolding.setHoldingType(HoldingType.ALLOCATED); // Default to ALLOCATED
        model.addAttribute("newHolding", newHolding);
        model.addAttribute("portfolios", portfolioRepository.findAllByOrderByDisplayOrderAsc());
        model.addAttribute("holdingTypes", HoldingType.values());
        return "dashboard";
    }

    /** Portfolio history page */
    @GetMapping("/portfolio/{portfolioId}/history")
    public String portfolioHistory(@PathVariable Long portfolioId, Model model) {
        PortfolioHistoryViewModel vm = rebalanceService.buildPortfolioHistory(portfolioId);
        model.addAttribute("vm", vm);
        return "portfolio-history";
    }

    /** Manual "refresh quotes now" button */
    @PostMapping("/quotes/refresh")
    public String refreshNow(RedirectAttributes redirectAttributes) {
        int updated = quoteService.fetchAndStoreQuotes();
        redirectAttributes.addFlashAttribute("message",
                "Quotes refreshed — " + updated + " holdings updated.");
        return "redirect:/";
    }

    /** Add a new holding */
    @PostMapping("/holdings")
    public String addHolding(
            @Valid @ModelAttribute("newHolding") Holding holding,
            BindingResult bindingResult,
            Model model,
            @RequestParam(required = false) Long portfolioId,
            @RequestParam(required = false) String holdingType,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("vm", rebalanceService.buildMultiPortfolioDashboard());
            model.addAttribute("portfolios", portfolioRepository.findAllByOrderByDisplayOrderAsc());
            model.addAttribute("holdingTypes", HoldingType.values());
            return "dashboard";
        }

        // Set holding type if provided
        if (holdingType != null && !holdingType.isEmpty()) {
            try {
                holding.setHoldingType(HoldingType.valueOf(holdingType));
            } catch (IllegalArgumentException e) {
                // Default to ALLOCATED if invalid value provided
                holding.setHoldingType(HoldingType.ALLOCATED);
            }
        }

        // For CASH holdings, enforce ticker="CASH" and clear name
        if (holding.getHoldingType() == HoldingType.CASH) {
            holding.setTicker("CASH");
            holding.setName("");
        } else {
            // For other types, normalize ticker to uppercase
            holding.setTicker(holding.getTicker().toUpperCase().trim());
        }

        // Set portfolio if provided
        if (portfolioId != null) {
            portfolioRepository.findById(portfolioId).ifPresent(holding::setPortfolio);
        }

        // If still no portfolio, assign default
        if (holding.getPortfolio() == null) {
            Portfolio defaultPortfolio = portfolioRepository.findAllByOrderByDisplayOrderAsc().stream()
                    .findFirst()
                    .orElseGet(() -> {
                        Portfolio p = Portfolio.builder()
                                .name("Main Portfolio")
                                .displayOrder(0)
                                .build();
                        return portfolioRepository.save(p);
                    });
            holding.setPortfolio(defaultPortfolio);
        }
        holdingRepository.save(holding);
        redirectAttributes.addFlashAttribute("message",
                "Added " + holding.getTicker() + " to " + holding.getPortfolio().getName() + ".");
        return "redirect:/";
    }

    /** Delete a holding */
    @PostMapping("/holdings/{id}/delete")
    public String deleteHolding(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        holdingRepository.findById(id).ifPresent(h -> {
            holdingRepository.delete(h);
            redirectAttributes.addFlashAttribute("message", "Removed " + h.getTicker());
        });
        return "redirect:/";
    }

    /** Update shares or target allocation for a holding */
    @PostMapping("/holdings/{id}/update")
    public String updateHolding(
            @PathVariable Long id,
            @RequestParam BigDecimal sharesOwned,
            @RequestParam BigDecimal targetAllocationPct,
            RedirectAttributes redirectAttributes) {

        holdingRepository.findById(id).ifPresent(h -> {
            h.setSharesOwned(sharesOwned);
            h.setTargetAllocationPct(targetAllocationPct);
            holdingRepository.save(h);
            redirectAttributes.addFlashAttribute("message", "Updated " + h.getTicker());
        });
        return "redirect:/";
    }

    /** Add a new portfolio */
    @PostMapping("/portfolios")
    public String addPortfolio(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            RedirectAttributes redirectAttributes) {

        if (name == null || name.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Portfolio name cannot be empty");
            return "redirect:/";
        }

        // Find the highest display order
        Integer maxOrder = portfolioRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(Portfolio::getDisplayOrder)
                .max(Integer::compareTo)
                .orElse(-1);

        Portfolio portfolio = Portfolio.builder()
                .name(name.trim())
                .description(description != null ? description.trim() : "")
                .displayOrder(maxOrder + 1)
                .build();
        portfolioRepository.save(portfolio);

        redirectAttributes.addFlashAttribute("message", "Created portfolio: " + name);
        return "redirect:/";
    }

    /** Delete a portfolio and all its holdings */
    @PostMapping("/portfolios/{id}/delete")
    public String deletePortfolio(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {

        portfolioRepository.findById(id).ifPresent(portfolio -> {
            String name = portfolio.getName();
            // Delete all holdings in this portfolio
            holdingRepository.findByPortfolio(portfolio).forEach(holdingRepository::delete);
            // Delete the portfolio
            portfolioRepository.delete(portfolio);
            redirectAttributes.addFlashAttribute("message", "Deleted portfolio: " + name);
        });
        return "redirect:/";
    }

    /** Bulk-edit holdings for a portfolio from pasted TSV data */
    @PostMapping("/portfolios/{id}/bulk-edit")
    @ResponseBody
    public ResponseEntity<BulkEditResult> bulkEdit(
            @PathVariable Long id,
            @RequestBody BulkEditRequest request) {

        request.setPortfolioId(id);   // path param takes precedence
        BulkEditResult result = bulkEditService.processBulkEdit(id, request.getTsvData());
        HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(result);
    }

    /** JSON endpoint — useful for auto-refreshing the dashboard with fetch() */
    @GetMapping("/api/dashboard")
    @ResponseBody
    public ResponseEntity<MultiPortfolioDashboardViewModel> dashboardJson() {
        return ResponseEntity.ok(rebalanceService.buildMultiPortfolioDashboard());
    }
}
