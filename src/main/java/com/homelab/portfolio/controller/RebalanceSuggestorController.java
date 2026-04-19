package com.homelab.portfolio.controller;

import com.homelab.portfolio.dto.RebalanceSuggestorRequest;
import com.homelab.portfolio.dto.RebalanceSuggestorResponse;
import com.homelab.portfolio.repository.PortfolioRepository;
import com.homelab.portfolio.service.RebalanceSuggestorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/rebalance")
@Slf4j
@RequiredArgsConstructor
public class RebalanceSuggestorController {

    private final RebalanceSuggestorService rebalanceSuggestorService;
    private final PortfolioRepository portfolioRepository;

    @GetMapping
    public String showRebalanceSuggestor(Model model) {
        RebalanceSuggestorRequest defaultRequest = new RebalanceSuggestorRequest();
        defaultRequest.setAllowFractionalShares(false);
        defaultRequest.setUseNewMoney(false);
        
        model.addAttribute("request", defaultRequest);
        model.addAttribute("portfolios", portfolioRepository.findAllByOrderByDisplayOrderAsc());
        return "rebalance";
    }

    @PostMapping("/suggest")
    public String generateSuggestion(
            @ModelAttribute("request") RebalanceSuggestorRequest request,
            Model model) {
        
        // Validation check
        if (request.getPortfolioId() == null) {
            model.addAttribute("error", "Please select a portfolio.");
            model.addAttribute("portfolios", portfolioRepository.findAllByOrderByDisplayOrderAsc());
            return "rebalance";
        }

        try {
            RebalanceSuggestorResponse response = rebalanceSuggestorService.suggestRebalance(request);
            model.addAttribute("response", response);
        } catch (Exception e) {
            log.error("Error generating rebalance suggestion", e);
            model.addAttribute("error", "Failed to generate suggestion: " + e.getMessage());
        }

        model.addAttribute("portfolios", portfolioRepository.findAllByOrderByDisplayOrderAsc());
        return "rebalance";
    }
}
