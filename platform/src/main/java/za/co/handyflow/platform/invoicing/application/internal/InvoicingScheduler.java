package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.invoicing.domain.model.Invoice;
import za.co.handyflow.platform.invoicing.domain.model.InvoiceLineItem;
import za.co.handyflow.platform.invoicing.domain.model.Quote;
import za.co.handyflow.platform.invoicing.domain.model.RecurringSchedule;
import za.co.handyflow.platform.invoicing.domain.model.RecurringScheduleStatus;
import za.co.handyflow.platform.invoicing.domain.repository.InvoiceRepository;
import za.co.handyflow.platform.invoicing.domain.repository.QuoteRepository;
import za.co.handyflow.platform.invoicing.domain.repository.RecurringScheduleRepository;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
class InvoicingScheduler {

    private static final int[] OVERDUE_ESCALATION_THRESHOLDS = {1, 3, 7, 14, 30};

    private final QuoteRepository              quoteRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final RecurringScheduleRepository  scheduleRepository;
    private final InvoiceRepository            invoiceRepository;
    private final InvoiceNumberGenerator       invoiceNumberGenerator;
    private final InvoicePaymentTermsResolver  paymentTermsResolver;
    private final StaffNotifier                staffNotifier;
    private final QuoteService                 quoteService;
    private final EmailService                 emailService;
    private final CrmFacade                    crmFacade;
    // NEW: sign-off given to close out the recurring-invoice email send.
    // Same two dependencies QuoteService.convertToInvoice already uses for
    // the identical "generate PDF, email it, don't let a bounce roll back
    // an already-saved invoice" pattern.
    private final InvoicePdfService            invoicePdfService;
    private final TenantFacade                 tenantFacade;
    // Hard invoice cap (Decision 2): a recurring invoice that would go over the cap is delayed, never skipped or forced.
    private final za.co.handyflow.platform.usage.UsageCapFacade usageCap;
    private final RecurringInvoiceQueue        delayQueue;
    private final org.springframework.transaction.support.TransactionTemplate tx;

    /** How long a delayed recurring invoice may wait, from its original billing date, before the administrators are escalated to. */
    @org.springframework.beans.factory.annotation.Value("${handyflow.invoicing.recurring-max-delay-days:7}")
    private int maxDelayDays;

    private static final java.time.ZoneId SA = java.time.ZoneId.of("Africa/Johannesburg");

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

    @Scheduled(cron = "0 35 2 * * *")
    void sendQuoteExpiryReminders() {
        Instant now = Instant.now();
        Instant warningThreshold = now.plus(3, ChronoUnit.DAYS);
        List<Quote> needingReminder = quoteRepository.findQuotesNeedingExpiryReminder(warningThreshold, now);
        log.info("Quote expiry reminders: {} quote(s) due", needingReminder.size());
        needingReminder.forEach(quoteService::sendExpiryReminder);
    }

    @Scheduled(cron = "0 40 2 * * *")
    void detectOverdueInvoices() {
        LocalDate today = LocalDate.now();
        var overdue = invoiceRepository.findOverdueInvoices(today);
        log.info("Overdue invoice detection: {} invoice(s) currently overdue", overdue.size());

        for (var invoice : overdue) {
            try {
                markOverdueAndMaybeEscalate(invoice, today);
            } catch (Exception e) {
                log.error("Failed to process overdue invoice={}: {}", invoice.getId(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    protected void markOverdueAndMaybeEscalate(Invoice invoice, LocalDate today) {
        invoice.markOverdue();
        invoiceRepository.save(invoice);

        int daysOverdue = (int) ChronoUnit.DAYS.between(invoice.getDueDate(), today);
        Integer threshold = invoice.nextOverdueReminderThreshold(daysOverdue, OVERDUE_ESCALATION_THRESHOLDS);

        if (threshold == null) {
            return;
        }

        String urgencyLabel = urgencyLabelFor(threshold);

        try {
            String clientEmail = resolveClientEmail(invoice);
            if (clientEmail != null && !clientEmail.isBlank()) {
                String clientName = resolveClientName(invoice);
                String amount = "R " + String.format(java.util.Locale.US, "%,.2f",
                        invoice.getBalanceDue());   // finance patch F8: credit notes count
                // FIX: same "PDF and the email that should carry it exist
                // independently" gap as ClinicBillingService's claim-status
                // email — this fires an escalating collections email
                // ("Payment reminder" through "Final notice", up to 5 times
                // per invoice) telling the client to pay, without ever
                // attaching what they're supposed to pay. invoicePdfService
                // was already injected into this exact class for the
                // recurring-invoice-generated email below — reused here.
                byte[] pdfBytes = invoicePdfService.generateInvoicePdf(invoice.getId(), invoice.getTenantId());
                emailService.sendWithAttachment(
                        clientEmail,
                        urgencyLabel + ": Invoice " + invoice.getInvoiceNumber() + " is overdue",
                        EmailTemplates.invoiceOverdueReminderEscalating(
                                clientName, invoice.getInvoiceNumber(), amount, daysOverdue,
                                "Your supplier", urgencyLabel),
                        invoice.getInvoiceNumber() + ".pdf",
                        pdfBytes
                );
            } else {
                log.warn("Invoice={} overdue threshold={}d reached but no client email on file — reminder not sent",
                        invoice.getInvoiceNumber(), threshold);
            }
        } catch (Exception e) {
            log.warn("Overdue reminder email not sent for invoice={}: {}", invoice.getId(), e.getMessage());
        }

        staffNotifier.notify(invoice.getTenantId(), NotificationType.INVOICE_OVERDUE,
                urgencyLabel + ": " + invoice.getInvoiceNumber(),
                "Invoice " + invoice.getInvoiceNumber() + " is now " + daysOverdue + " day(s) overdue.",
                "/invoices", invoice.getId().toString());

        invoice.recordOverdueReminderSent(threshold, Instant.now());
        invoiceRepository.save(invoice);

        log.info("Overdue reminder sent invoice={} threshold={}d actualDaysOverdue={}",
                invoice.getInvoiceNumber(), threshold, daysOverdue);
    }

    private String urgencyLabelFor(int thresholdDays) {
        return switch (thresholdDays) {
            case 1  -> "Payment reminder";
            case 3  -> "Second reminder";
            case 7  -> "Overdue notice";
            case 14 -> "Urgent: payment required";
            default -> "Final notice";
        };
    }

    @Scheduled(cron = "0 45 2 * * *")
    void fireRecurringSchedules() {
        var due = scheduleRepository.findDueSchedules(Instant.now());
        log.info("Recurring scheduler: {} schedule(s) due", due.size());

        for (var schedule : due) {
            UUID scheduleId = schedule.getId();
            try {
                tx.executeWithoutResult(st -> fireOne(scheduleId));
            } catch (Exception e) {
                log.error("Failed to spawn invoice for schedule={} tenant={}: {}",
                        schedule.getId(), schedule.getTenantId(), e.getMessage(), e);

                staffNotifier.notify(schedule.getTenantId(), NotificationType.RECURRING_SCHEDULE_FAILED,
                        "Recurring billing failed: " + schedule.getTitle(),
                        "The recurring schedule \"" + schedule.getTitle()
                                + "\" failed to generate an invoice tonight: " + e.getMessage(),
                        "/recurring", schedule.getId().toString());
            }
        }
        drainDelayedInvoices();
    }

    /** One due schedule, in one transaction, so the cap check, the invoice and the queue entry stand or fall together. */
    private void fireOne(UUID scheduleId) {
        var schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null || schedule.getStatus() != RecurringScheduleStatus.ACTIVE || schedule.getNextRunAt().isAfter(Instant.now())) return;
        var tenantId = schedule.getTenantId();
        var cap = usageCap.checkAndLock(tenantId);
        if (RecurringQueueRules.shouldQueue(cap.enforced(), cap.allowed(), delayQueue.hasQueued(tenantId))) {
            delay(schedule, cap);
            return;
        }
        spawnInvoiceForSchedule(schedule, null);
    }

    /** The occurrence waits with its original billing date and the period it covers; the schedule moves on to the next one. */
    private void delay(RecurringSchedule schedule, za.co.handyflow.platform.usage.UsageCapFacade.Decision cap) {
        Instant scheduled = schedule.getNextRunAt();
        LocalDate start = scheduled.atZone(SA).toLocalDate();
        LocalDate end = RecurringQueueRules.periodEnd(start, schedule.followingRun(scheduled).atZone(SA).toLocalDate());
        boolean added = delayQueue.enqueue(schedule.getTenantId(), schedule.getId(), start, start, end);
        schedule.deferOccurrence(scheduled);
        scheduleRepository.save(schedule);
        log.warn("RECURRING-DELAYED tenant={} schedule={} scheduledFor={} cap={}/{} period={}", schedule.getTenantId().getValue(),
                schedule.getId(), start, cap.used(), cap.limit(), cap.period());
        if (added) {
            String why = cap.allowed() ? "Earlier delayed invoices are being issued first."
                    : "You have used " + cap.used() + " of the " + cap.limit() + " invoices your plan allows for " + cap.period() + ".";
            staffNotifier.notify(schedule.getTenantId(), NotificationType.RECURRING_INVOICE_DELAYED,
                    "Recurring invoice delayed: " + schedule.getTitle(),
                    "The invoice for \"" + schedule.getTitle() + "\" due on " + start + " (service period " + start + " to " + end
                            + ") has been delayed, not skipped. " + why + " It will be issued as soon as there is room, with today's date as its issue date.",
                    "/recurring", schedule.getId().toString());
        }
    }

    /** Issues delayed occurrences, oldest first, wherever the cap now allows. Each is issued exactly once. */
    void drainDelayedInvoices() {
        java.util.Set<UUID> full = new java.util.HashSet<>();
        for (UUID id : delayQueue.queuedIds()) {
            try {
                tx.executeWithoutResult(st -> issueDelayed(id, full));
            } catch (Exception e) {
                log.error("Could not issue delayed recurring invoice queue={}: {}", id, e.getMessage(), e);
            }
        }
        escalateOverdue();
    }

    private void issueDelayed(UUID queueId, java.util.Set<UUID> full) {
        var entry = delayQueue.lockQueued(queueId).orElse(null);   // gone or already issued: nothing to do
        if (entry == null || full.contains(entry.tenantId())) return;
        var tenantId = TenantId.of(entry.tenantId());
        var cap = usageCap.checkAndLock(tenantId);
        if (cap.enforced() && !cap.allowed()) {
            full.add(entry.tenantId());
            delayQueue.attempted(queueId, "Waiting for room: " + cap.used() + " of " + cap.limit() + " invoices used for " + cap.period() + ".");
            return;
        }
        var schedule = scheduleRepository.findById(entry.scheduleId()).orElse(null);
        if (schedule == null || schedule.getStatus() == RecurringScheduleStatus.PAUSED || schedule.getStatus() == RecurringScheduleStatus.CANCELLED) {
            delayQueue.attempted(queueId, "The recurring schedule is " + (schedule == null ? "missing" : schedule.getStatus().name().toLowerCase())
                    + ". Resume it to issue this invoice.");
            return;
        }
        spawnInvoiceForSchedule(schedule, entry);
    }

    /** Tells the administrators, once, about an occurrence that has waited longer than the configured limit. */
    private void escalateOverdue() {
        for (var e : delayQueue.needingEscalation(LocalDate.now(SA), maxDelayDays)) {
            try {
                staffNotifier.notify(TenantId.of(e.tenantId()), NotificationType.RECURRING_INVOICE_DELAY_ESCALATED,
                        "Recurring invoice still waiting: " + e.scheduleTitle(),
                        "The invoice for \"" + e.scheduleTitle() + "\" due on " + e.scheduledFor() + " (service period " + e.periodStart() + " to "
                                + e.periodEnd() + ") has been waiting " + RecurringQueueRules.daysDelayed(e.scheduledFor(), LocalDate.now(SA))
                                + " days, more than the " + maxDelayDays + "-day limit. It cannot be issued until your plan has room: upgrade your plan or wait for the new month.",
                        "/recurring", e.scheduleId().toString());
                delayQueue.escalated(e.id());
                log.warn("RECURRING-ESCALATED tenant={} schedule={} scheduledFor={}", e.tenantId(), e.scheduleId(), e.scheduledFor());
            } catch (RuntimeException ex) {
                log.warn("Could not escalate delayed recurring invoice {}: {}", e.id(), ex.getMessage());
            }
        }
    }

    /** {@code delayed} is the queue entry when this occurrence was held back by the cap; it is then issued late, once. */
    protected void spawnInvoiceForSchedule(RecurringSchedule schedule, RecurringInvoiceQueue.Entry delayed) {
        var tenantId      = schedule.getTenantId();
        var invoiceNumber = invoiceNumberGenerator.next(tenantId);

        var invoice = Invoice.createFromSchedule(
                tenantId,
                schedule.getCustomerId(),
                schedule.getId(),
                invoiceNumber,
                delayed == null ? schedule.getTitle()
                        : RecurringQueueRules.delayedTitle(schedule.getTitle(), delayed.periodStart(), delayed.periodEnd()),
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
        LocalDate dueDate = paymentTermsResolver.resolveDueDate(tenantId, LocalDate.now());
        invoice.markIssued(dueDate);
        // INV-01: this path used to issue the invoice without telling the ledger. The posting is once per invoice, so
        // it is safe if anything else also announces it.
        eventPublisher.publishEvent(za.co.handyflow.platform.invoicing.InvoiceIssuedEvent.of(
                tenantId, invoice.getId(), invoice.getInvoiceNumber(), invoice.getCustomerId(),
                invoice.getSubtotal(), invoice.getVatTotal(), invoice.getTotal()));
        if (schedule.getCustomerId() != null) {
            // On the customer's timeline. A timeline failure must not fail the invoice.
            try {
                crmFacade.notifyInvoiceLinked(tenantId, schedule.getCustomerId(), invoice.getId(), null);
            } catch (RuntimeException e) {
                log.warn("Could not record invoice={} on the customer timeline: {}", invoiceNumber, e.getMessage());
            }
        }

        if (delayed == null) {
            schedule.markRan(Instant.now());
            scheduleRepository.save(schedule);
        } else {
            // Already moved on when it was delayed; here the waiting occurrence is closed against the invoice it became.
            delayQueue.issued(delayed.id(), invoice.getId());
            log.info("Issued delayed recurring invoice={} for occurrence {} (period {} to {})", invoiceNumber,
                    delayed.scheduledFor(), delayed.periodStart(), delayed.periodEnd());
        }

        log.info("Spawned recurring invoice={} from schedule={} nextRun={}",
                invoiceNumber, schedule.getId(), schedule.getNextRunAt());

        // FIXED: was resolved-but-not-sent. Now mirrors
        // QuoteService.convertToInvoice's exact pattern — generate the PDF,
        // email it with attachment, and never let an SMTP failure roll back
        // the invoice that's already correctly saved and issued above.
        try {
            String clientEmail = resolveClientEmail(invoice);
            if (clientEmail != null && !clientEmail.isBlank()) {
                String clientName = resolveClientName(invoice);
                tenantFacade.findTenantDetails(tenantId).ifPresent(tenant -> {
                    byte[] pdfBytes = invoicePdfService.generateInvoicePdf(invoice.getId(), tenantId);
                    String amount = "R " + String.format(java.util.Locale.US, "%,.2f", invoice.getTotal());
                    // FIXED: was reusing invoiceGeneratedWithPdf, whose copy
                    // says "generated from your accepted quote" — factually
                    // wrong for a recurring/scheduled invoice, which has no
                    // quote involved. Now uses a dedicated template with
                    // correct wording and a reference to the schedule name.
                    emailService.sendWithAttachment(
                            clientEmail,
                            "Invoice " + invoiceNumber + " — " + clientName,
                            EmailTemplates.recurringInvoiceGeneratedWithPdf(
                                    tenant.companyName(), invoiceNumber, clientName, amount, schedule.getTitle()),
                            invoiceNumber + ".pdf",
                            pdfBytes
                    );
                });
            } else {
                log.warn("Recurring invoice={} has no resolvable client email (schedule={}) — not emailed",
                        invoiceNumber, schedule.getId());
            }
        } catch (Exception e) {
            log.warn("Recurring invoice email not sent for invoice={}: {}", invoice.getId(), e.getMessage());
        }

        staffNotifier.notify(tenantId, NotificationType.INVOICE_ISSUED,
                "Recurring invoice generated: " + invoiceNumber,
                "Schedule \"" + schedule.getTitle() + "\" generated invoice " + invoiceNumber + ".",
                "/invoices", invoice.getId().toString());
    }

    // ── Shared client-resolution helpers ──────────────────────────────────────

    private String resolveClientEmail(Invoice invoice) {
        UUID customerId = invoice.getCustomerId();
        if (customerId != null) {
            TenantId tenantId = invoice.getTenantId();
            return crmFacade.findCustomerById(tenantId, customerId)
                    .map(c -> c.email())
                    .orElse(null);
        }
        return invoice.getWalkinClientEmail();
    }

    private String resolveClientName(Invoice invoice) {
        UUID customerId = invoice.getCustomerId();
        if (customerId != null) {
            TenantId tenantId = invoice.getTenantId();
            return crmFacade.findCustomerById(tenantId, customerId)
                    .map(c -> c.name()).orElse("Customer");
        }
        return invoice.getWalkinClientName() != null ? invoice.getWalkinClientName() : "Client";
    }
}