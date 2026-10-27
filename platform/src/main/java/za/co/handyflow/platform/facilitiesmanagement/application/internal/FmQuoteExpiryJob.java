package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmQuote;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmQuoteRepository;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.SchedulerLease;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Daily 09:10 sweep: a SENT quote whose validity has passed is recorded as EXPIRED and the administrators are told, so a
 * quote that went unanswered does not sit open forever. (The portal and staff views already show it as EXPIRED the day
 * after its validity ends; this makes the record agree.) One quote per transaction; a failure on one does not stop the rest.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FmQuoteExpiryJob {

    private final FmQuoteRepository quoteRepository;
    private final FmAuditService auditService;
    private final FmAlerts alerts;
    private final SchedulerLease schedulerLease;
    private final FmSyncExecutor executor;

    @Scheduled(cron = "0 10 9 * * *")
    public void runDailySweep() {
        schedulerLease.runExclusively("fm-quote-expiry", Duration.ofMinutes(10), () -> {
            int n = sweep(FmTime.today());
            log.info("[FM] Quote expiry sweep — {} quote(s) expired", n);
        });
    }

    /** Package-visible for tests; returns the number of quotes expired. */
    int sweep(LocalDate today) {
        List<FmQuote> due = quoteRepository.findSentExpiredAcrossTenants(today);
        int expired = 0;
        for (FmQuote q : due) {
            try {
                executor.inNewTransaction(() -> {
                    q.expire(today);
                    quoteRepository.save(q);
                    auditService.recordSystem(q.getTenantId(), "QUOTE_EXPIRED", "QUOTE", q.getId(), q.getQuoteNumber() + " validUntil=" + q.getValidUntil());
                    return null;
                });
                alerts.toAdmins(q.getTenantId(), NotificationType.FM_QUOTE_EXPIRED, "Quote expired: " + q.getQuoteNumber(),
                        q.getTitle() + " (R" + q.getTotal() + ") passed " + q.getValidUntil() + " without an answer.",
                        "/facilitiesmanagement/quotes/" + q.getId(), q.getId().toString());
                expired++;
            } catch (Exception e) {
                log.error("[FM] Could not expire quote {}: {}", q.getId(), e.getMessage(), e);
            }
        }
        return expired;
    }
}
