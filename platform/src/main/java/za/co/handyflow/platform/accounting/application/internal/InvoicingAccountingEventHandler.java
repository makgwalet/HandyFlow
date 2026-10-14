package za.co.handyflow.platform.accounting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.accounting.application.PostingKeys;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.accounting.domain.repository.AccAccountRepository;
import za.co.handyflow.platform.accounting.domain.repository.AccBankAccountRepository;
import za.co.handyflow.platform.invoicing.CreditNoteIssuedEvent;
import za.co.handyflow.platform.invoicing.InvoiceIssuedEvent;
import za.co.handyflow.platform.invoicing.InvoicePaymentRecordedEvent;
import za.co.handyflow.platform.invoicing.ReceiptRecordedEvent;
import za.co.handyflow.platform.invoicing.RefundIssuedEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * FIX: backlog 1.6 — invoice issuance and payment now post to the
 * general ledger. Lives inside accounting (not invoicing) specifically
 * so it can import invoicing's own published events without creating a
 * circular module dependency — accounting already depends on invoicing
 * (AccountingService injects InvoicingFacade for its own AR-aging
 * report), so this direction is already an allowed boundary; the
 * reverse (invoicing depending on accounting) is not, and adding it
 * would have created exactly that cycle. See InvoiceIssuedEvent's own
 * Javadoc for the full rationale.
 * <p>
 * Uses AccountingService directly, not AccountingFacade — this class
 * lives inside the accounting module itself, so there's no boundary to
 * cross; going through the facade from inside its own module would be
 * unnecessary indirection, not the pattern the facade exists for.
 * <p>
 * Account codes (1100 Accounts Receivable, 4000 Revenue, 2100 VAT
 * Output) are the real, confirmed seeded codes from
 * ChartOfAccountsSeeder — not invented. createdBy is passed as null on
 * every journal here, matching the exact convention
 * AccountingService.createJournalEntry()'s own 2-arg overload already
 * documents: "createdBy stays null for AP- and reconciliation-triggered
 * journals — correct, since those already went through their own
 * review elsewhere and don't need a second one here." An invoice being
 * issued or a payment being recorded is exactly that kind of
 * already-reviewed business action.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class InvoicingAccountingEventHandler {

    private static final String AR_ACCOUNT_CODE          = "1100";
    private static final String REVENUE_ACCOUNT_CODE     = "4000";
    private static final String VAT_OUTPUT_ACCOUNT_CODE  = "2100";

    private final AccAccountRepository     accountRepo;
    private final AccBankAccountRepository bankAccountRepo;
    // Finance patch F4: posts in its own transaction and queues failures where people can see and retry them.
    private final LedgerPostingService     ledgerPosting;

    @ApplicationModuleListener
    void onInvoiceIssued(InvoiceIssuedEvent event) {
        String key = PostingKeys.of("invoicing", "invoice-issued", event.invoiceId());
        String summary = "Invoice issued: " + event.invoiceNumber();
        try {
            UUID arAccountId = findAccountByCode(event.tenantId().getValue(), AR_ACCOUNT_CODE);
            UUID revenueAccountId = findAccountByCode(event.tenantId().getValue(), REVENUE_ACCOUNT_CODE);
            if (arAccountId == null || revenueAccountId == null) {
                ledgerPosting.recordFailure(event.tenantId(), key, summary,
                        "The chart of accounts has no Accounts Receivable (" + AR_ACCOUNT_CODE + ") or Revenue ("
                                + REVENUE_ACCOUNT_CODE + ") account");
                return;
            }

            boolean hasVat = event.vatTotal() != null && event.vatTotal().compareTo(BigDecimal.ZERO) > 0;
            UUID vatAccountId = null;
            if (hasVat) {
                vatAccountId = findAccountByCode(event.tenantId().getValue(), VAT_OUTPUT_ACCOUNT_CODE);
                if (vatAccountId == null) {
                    ledgerPosting.recordFailure(event.tenantId(), key, summary,
                            "The chart of accounts has no VAT Output (" + VAT_OUTPUT_ACCOUNT_CODE + ") account");
                    return;
                }
            }

            List<CreateJournalEntryRequest.JournalLineRequest> lines = new ArrayList<>();
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                    arAccountId, "Invoice issued — " + event.invoiceNumber(), event.total(), null));
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                    revenueAccountId, "Revenue — " + event.invoiceNumber(), null, event.subtotal()));
            if (hasVat) {
                lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                        vatAccountId, "VAT output — " + event.invoiceNumber(), null, event.vatTotal()));
            }

            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    LocalDate.now(), "Invoice issued: " + event.invoiceNumber(),
                    event.invoiceNumber(), "MANUAL", lines);

            // Finance patch F3/F4: once per invoice, in its own transaction; a failure is queued.
            ledgerPosting.postOnceOrRecord(event.tenantId(), key, req);
            log.info("[Accounting] Posted issuance journal for invoice={} tenant={}", event.invoiceNumber(), event.tenantId());
        } catch (Exception e) {
            // Same principle as every other cross-module side-effect hookup
            // in this codebase: the invoice is already saved/issued by the
            // time this listener runs — a posting failure must never look
            // like it affected that.
            log.error("[Accounting] Failed to post issuance journal for invoice={} tenant={}: {}",
                    event.invoiceNumber(), event.tenantId(), e.getMessage(), e);
        }
    }

    @ApplicationModuleListener
    void onInvoicePaymentRecorded(InvoicePaymentRecordedEvent event) {
        // Finance patch F3: once per payment. Events stored before F3 carry no payment id; they get a fresh one here.
        String key = PostingKeys.of("invoicing", "payment-received",
                event.paymentEventId() != null ? event.paymentEventId() : UUID.randomUUID());
        String summary = "Payment received: " + event.invoiceNumber() + " R " + event.amountPaid();
        try {
            if (event.bankAccountId() == null) {
                // Never guess which bank account received real money. Finance patch F4: the payment dialog now asks for
                // it; a payment recorded without it is queued for a person (only where the business keeps bank accounts
                // here, so tenants that do not use Accounting do not fill the queue).
                log.warn("[Accounting] Payment recorded for invoice={} tenant={} with no bankAccountId — not posted",
                        event.invoiceNumber(), event.tenantId());
                if (!bankAccountRepo.findAllActive(event.tenantId()).isEmpty()) {
                    ledgerPosting.recordFailure(event.tenantId(), key, summary,
                            "The payment was recorded without the bank account that received it. Post it by hand "
                                    + "(debit the bank, credit Accounts Receivable " + AR_ACCOUNT_CODE + "), then dismiss this");
                }
                return;
            }

            var bankAccount = bankAccountRepo.findActiveById(event.tenantId(), event.bankAccountId()).orElse(null);
            if (bankAccount == null || bankAccount.getAccountId() == null) {
                ledgerPosting.recordFailure(event.tenantId(), key, summary,
                        "The bank account that received the payment is missing or not linked to a ledger account");
                return;
            }

            UUID arAccountId = findAccountByCode(event.tenantId().getValue(), AR_ACCOUNT_CODE);
            if (arAccountId == null) {
                ledgerPosting.recordFailure(event.tenantId(), key, summary,
                        "The chart of accounts has no Accounts Receivable (" + AR_ACCOUNT_CODE + ") account");
                return;
            }

            List<CreateJournalEntryRequest.JournalLineRequest> lines = List.of(
                    new CreateJournalEntryRequest.JournalLineRequest(
                            bankAccount.getAccountId(), "Payment received — " + event.invoiceNumber(), event.amountPaid(), null),
                    new CreateJournalEntryRequest.JournalLineRequest(
                            arAccountId, "Payment received — " + event.invoiceNumber(), null, event.amountPaid()));

            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    LocalDate.now(), "Payment received: " + event.invoiceNumber(),
                    event.invoiceNumber(), "PAYMENT", lines);

            ledgerPosting.postOnceOrRecord(event.tenantId(), key, req);
            log.info("[Accounting] Posted payment journal for invoice={} tenant={}", event.invoiceNumber(), event.tenantId());
        } catch (Exception e) {
            log.error("[Accounting] Failed to post payment journal for invoice={} tenant={}: {}",
                    event.invoiceNumber(), event.tenantId(), e.getMessage(), e);
        }
    }

    /**
     * Finance patch F8: money received from a customer, posted once per receipt: Dr the bank account it arrived in,
     * Cr Accounts Receivable for the whole amount, whether it settled invoices or sits on the customer's account as credit.
     */
    @ApplicationModuleListener
    void onReceiptRecorded(ReceiptRecordedEvent event) {
        String key = PostingKeys.of("invoicing", "receipt", event.receiptId());
        String what = event.invoiceNumbers() != null && !event.invoiceNumbers().isBlank() ? event.invoiceNumbers() : "on account";
        String summary = "Payment received: " + event.receiptNumber() + " R " + event.amount() + " (" + what + ")";
        try {
            if (event.bankAccountId() == null) {
                log.warn("[Accounting] Receipt={} tenant={} has no bank account — not posted", event.receiptNumber(), event.tenantId());
                if (!bankAccountRepo.findAllActive(event.tenantId()).isEmpty()) {
                    ledgerPosting.recordFailure(event.tenantId(), key, summary,
                            "The payment was recorded without the bank account that received it. Post it by hand "
                                    + "(debit the bank, credit Accounts Receivable " + AR_ACCOUNT_CODE + "), then dismiss this");
                }
                return;
            }
            var bankAccount = bankAccountRepo.findActiveById(event.tenantId(), event.bankAccountId()).orElse(null);
            if (bankAccount == null || bankAccount.getAccountId() == null) {
                ledgerPosting.recordFailure(event.tenantId(), key, summary,
                        "The bank account that received the payment is missing or not linked to a ledger account");
                return;
            }
            UUID arAccountId = findAccountByCode(event.tenantId().getValue(), AR_ACCOUNT_CODE);
            if (arAccountId == null) {
                ledgerPosting.recordFailure(event.tenantId(), key, summary,
                        "The chart of accounts has no Accounts Receivable (" + AR_ACCOUNT_CODE + ") account");
                return;
            }
            String memo = "Payment received — " + event.receiptNumber() + " (" + what + ")";
            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    event.receivedOn() != null ? event.receivedOn() : LocalDate.now(), summary,
                    event.reference() != null ? event.reference() : event.receiptNumber(), "PAYMENT", List.of(
                    new CreateJournalEntryRequest.JournalLineRequest(bankAccount.getAccountId(), memo, event.amount(), null),
                    new CreateJournalEntryRequest.JournalLineRequest(arAccountId, memo, null, event.amount())));
            ledgerPosting.postOnceOrRecord(event.tenantId(), key, req);
            log.info("[Accounting] Posted receipt={} tenant={}", event.receiptNumber(), event.tenantId());
        } catch (Exception e) {
            log.error("[Accounting] Failed to post receipt={} tenant={}: {}", event.receiptNumber(), event.tenantId(), e.getMessage(), e);
        }
    }

    /** Finance patch F9: a refund of customer credit, posted once: Dr Accounts Receivable, Cr the bank it was paid from. */
    @ApplicationModuleListener
    void onRefundIssued(RefundIssuedEvent event) {
        String key = PostingKeys.of("invoicing", "refund", event.refundId());
        String summary = "Refund to customer: " + event.refundNumber() + " R " + event.amount() + " (credit " + event.receiptNumber() + ")";
        try {
            var bankAccount = bankAccountRepo.findActiveById(event.tenantId(), event.bankAccountId()).orElse(null);
            UUID arAccountId = findAccountByCode(event.tenantId().getValue(), AR_ACCOUNT_CODE);
            if (bankAccount == null || bankAccount.getAccountId() == null || arAccountId == null) {
                ledgerPosting.recordFailure(event.tenantId(), key, summary, arAccountId == null
                        ? "The chart of accounts has no Accounts Receivable (" + AR_ACCOUNT_CODE + ") account"
                        : "The bank account the refund was paid from is missing or not linked to a ledger account");
                return;
            }
            String memo = "Refund " + event.refundNumber() + " of credit " + event.receiptNumber();
            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    event.refundedOn() != null ? event.refundedOn() : LocalDate.now(), summary,
                    event.reference() != null ? event.reference() : event.refundNumber(), "PAYMENT", List.of(
                    new CreateJournalEntryRequest.JournalLineRequest(arAccountId, memo, event.amount(), null),
                    new CreateJournalEntryRequest.JournalLineRequest(bankAccount.getAccountId(), memo, null, event.amount())));
            ledgerPosting.postOnceOrRecord(event.tenantId(), key, req);
            log.info("[Accounting] Posted refund={} tenant={}", event.refundNumber(), event.tenantId());
        } catch (Exception e) {
            log.error("[Accounting] Failed to post refund={} tenant={}: {}", event.refundNumber(), event.tenantId(), e.getMessage(), e);
        }
    }

    /**
     * Finance patch F1: a credit note reverses part of an invoice's issuance journal. Debit Revenue (subtotal), Debit VAT
     * output (vatTotal), Credit Accounts Receivable (total). Same account codes and the same never-fail-the-source rule as
     * {@link #onInvoiceIssued}.
     */
    @ApplicationModuleListener
    void onCreditNoteIssued(CreditNoteIssuedEvent event) {
        String key = PostingKeys.of("invoicing", "credit-note-issued", event.creditNoteId());
        String summary = "Credit note issued: " + event.creditNoteNumber();
        try {
            UUID tenant = event.tenantId().getValue();
            UUID arAccountId = findAccountByCode(tenant, AR_ACCOUNT_CODE);
            UUID revenueAccountId = findAccountByCode(tenant, REVENUE_ACCOUNT_CODE);
            if (arAccountId == null || revenueAccountId == null) {
                ledgerPosting.recordFailure(event.tenantId(), key, summary,
                        "The chart of accounts has no Accounts Receivable (" + AR_ACCOUNT_CODE + ") or Revenue ("
                                + REVENUE_ACCOUNT_CODE + ") account");
                return;
            }
            boolean hasVat = event.vatTotal() != null && event.vatTotal().compareTo(BigDecimal.ZERO) > 0;
            UUID vatAccountId = null;
            if (hasVat) {
                vatAccountId = findAccountByCode(tenant, VAT_OUTPUT_ACCOUNT_CODE);
                if (vatAccountId == null) {
                    ledgerPosting.recordFailure(event.tenantId(), key, summary,
                            "The chart of accounts has no VAT Output (" + VAT_OUTPUT_ACCOUNT_CODE + ") account");
                    return;
                }
            }

            String ref = event.creditNoteNumber() + (event.invoiceNumber() != null ? " (" + event.invoiceNumber() + ")" : "");
            List<CreateJournalEntryRequest.JournalLineRequest> lines = new ArrayList<>();
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                    revenueAccountId, "Revenue reversed — " + ref, event.subtotal(), null));
            if (hasVat) {
                lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                        vatAccountId, "VAT output reversed — " + ref, event.vatTotal(), null));
            }
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                    arAccountId, "Credit note — " + ref, null, event.total()));

            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    LocalDate.now(), "Credit note issued: " + ref,
                    event.creditNoteNumber(), "INVOICE", lines);

            // Finance patch F3/F4: once per credit note, in its own transaction; a failure is queued.
            ledgerPosting.postOnceOrRecord(event.tenantId(), key, req);
            log.info("[Accounting] Posted credit note journal for credit note={} tenant={}", event.creditNoteNumber(), event.tenantId());
        } catch (Exception e) {
            log.error("[Accounting] Failed to post credit note journal for credit note={} tenant={}: {}",
                    event.creditNoteNumber(), event.tenantId(), e.getMessage(), e);
        }
    }

    private UUID findAccountByCode(UUID tenantId, String code) {
        return accountRepo.findAllActive(TenantId.of(tenantId)).stream()
                .filter(a -> code.equals(a.getAccountCode()))
                .map(a -> a.getId())
                .findFirst()
                .orElse(null);
    }
}