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
                        invoice.getTotal().subtract(invoice.getAmountPaid()));
                emailService.send(
                        clientEmail,
                        urgencyLabel + ": Invoice " + invoice.getInvoiceNumber() + " is overdue",
                        EmailTemplates.invoiceOverdueReminderEscalating(
                                clientName, invoice.getInvoiceNumber(), amount, daysOverdue,
                                "Your supplier", urgencyLabel)
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
            try {
                spawnInvoiceForSchedule(schedule);
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
        LocalDate dueDate = paymentTermsResolver.resolveDueDate(tenantId, LocalDate.now());
        invoice.markIssued(dueDate);

        schedule.markRan(Instant.now());
        scheduleRepository.save(schedule);

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