package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.invoicing.domain.repository.QuoteRepository;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
class InvoicingScheduler {

    private final QuoteRepository quoteRepository;

    // WHY 2:30 AM? After billing scheduler (2:00 AM). Stagger jobs
    // to avoid DB contention on overnight batch operations.
    @Scheduled(cron = "0 30 2 * * *")
    @Transactional
    void expireOverdueQuotes() {
        var expired = quoteRepository.findExpiredQuotes(Instant.now());
        expired.forEach(quote -> {
            quote.expire();
            quoteRepository.save(quote);
            log.info("Expired quote={} tenant={}", quote.getId(), quote.getTenantId());
        });
        if (!expired.isEmpty()) {
            log.info("Expired {} overdue quotes", expired.size());
        }
    }
}