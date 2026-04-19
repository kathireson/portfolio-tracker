package com.homelab.portfolio.scheduler;

import com.homelab.portfolio.service.QuoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

/**
 * Triggers a quote refresh at the start of each trading day.
 *
 * The cron expression and timezone are configurable in application.properties:
 *   portfolio.quote-fetch-cron=0 35 9 * * MON-FRI
 *   portfolio.scheduler-timezone=America/New_York
 *
 * The default fires at 9:35 AM ET, five minutes after NYSE open, giving
 * prices a moment to settle from the opening auction.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class QuoteRefreshScheduler {

    private final QuoteService quoteService;

    @Scheduled(
        cron = "${portfolio.quote-fetch-cron:0 35 9 * * MON-FRI}",
        zone = "${portfolio.scheduler-timezone:America/New_York}"
    )
    public void refreshQuotes() {
        log.info("Scheduled quote refresh starting at {}", ZonedDateTime.now());
        int updated = quoteService.fetchAndStoreQuotes();
        log.info("Scheduled quote refresh finished — {} holdings updated", updated);
    }
}
