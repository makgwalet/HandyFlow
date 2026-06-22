// invoicing/application/internal/InvoicingScheduler.java
package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.invoicing.domain.model.Invoice;
import za.co.handyflow.platform.invoicing.domain.model.InvoiceLineItem;
import za.co.handyflow.platform.invoicing.domain.model.RecurringSchedule;
import za.co.handyflow.platform.invoicing.domain.repository.InvoiceRepository;
import za.co.handyflow.platform.invoicing.domain.repository.QuoteRepository;
import za.co.handyflow.platform.invoicing.domain.repository.RecurringScheduleRepository;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
class InvoicingScheduler {

    private final QuoteRepository              quoteRepository;
    private final RecurringScheduleRepository  scheduleRepository;
    private final InvoiceRepository            invoiceRepository;
    private final InvoiceNumberGenerator       invoiceNumberGenerator;

    // WHY 2:30 AM? After billing scheduler (2:00 AM) — stagger to avoid DB
    // contention on overnight batch operations.
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

    /**
     * Fires AFTER the quote-expiry job (2:45 AM) so the two jobs don't compete
     * for DB connections on busy nights.
     *
     * WHY iterate then delegate to a separate @Transactional method?
     * A single bad schedule must not roll back invoices that already committed
     * cleanly.  We catch per-schedule failures, log them, and continue.
     */
    @Scheduled(cron = "0 45 2 * * *")
    void fireRecurringSchedules() {
        var due = scheduleRepository.findDueSchedules(Instant.now());
        log.info("Recurring scheduler: {} schedule(s) due", due.size());

        for (var schedule : due) {
            try {
                spawnInvoiceForSchedule(schedule);
            } catch (Exception e) {
                log.error("Failed to spawn invoice for schedule={} tenant={}: {}",
                        schedule.getId(), schedule.getTenantId(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    protected void spawnInvoiceForSchedule(RecurringSchedule schedule) {
        var tenantId      = schedule.getTenantId();
        var invoiceNumber = invoiceNumberGenerator.next(tenantId);

        var invoice = Invoice.createFromSchedule(
                tenantId,
                schedule.getCustomerId(),
                schedule.getId(),
                invoiceNumber,
                schedule.getTitle(),
                schedule.getSubtotal(),
                schedule.getVatTotal(),
                schedule.getTotal(),
                schedule.getWalkinClientName(),
                schedule.getWalkinClientEmail(),
                schedule.getWalkinClientPhone()
        );

        schedule.getLineItems().forEach(sli -> {
            var ili = InvoiceLineItem.create(
                    invoice, tenantId,
                    sli.getCatalogueItemId(), sli.getDescription(),
                    sli.getUnit(), sli.getQuantity(), sli.getUnitPrice(),
                    sli.getVatRate(), sli.getSortOrder()
            );
            invoice.addLineItem(ili);
        });

        invoice.recalculateTotals();
        invoiceRepository.save(invoice);
        invoice.markIssued();

        schedule.markRan(Instant.now());
        scheduleRepository.save(schedule);

        log.info("Spawned recurring invoice={} from schedule={} nextRun={}",
                invoiceNumber, schedule.getId(), schedule.getNextRunAt());
    }
}
