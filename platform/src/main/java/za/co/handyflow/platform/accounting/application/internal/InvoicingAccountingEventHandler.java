package za.co.handyflow.platform.accounting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.accounting.application.PostingKeys;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.accounting.dto.JournalEntryResponse;
import za.co.handyflow.platform.accounting.domain.repository.AccAccountRepository;
import za.co.handyflow.platform.accounting.domain.repository.AccBankAccountRepository;
import za.co.handyflow.platform.invoicing.CreditNoteIssuedEvent;
import za.co.handyflow.platform.invoicing.InvoiceIssuedEvent;
import za.co.handyflow.platform.invoicing.InvoicePaymentRecordedEvent;
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

    private final AccountingService        accountingService;
    private final AccAccountRepository     accountRepo;
    private final AccBankAccountRepository bankAccountRepo;

    @ApplicationModuleListener
    void onInvoiceIssued(InvoiceIssuedEvent event) {
        try {
            UUID arAccountId = findAccountByCode(event.tenantId().getValue(), AR_ACCOUNT_CODE);
            UUID revenueAccountId = findAccountByCode(event.tenantId().getValue(), REVENUE_ACCOUNT_CODE);
            if (arAccountId == null || revenueAccountId == null) {
                log.warn("[Accounting] Chart of Accounts missing AR ({}) or Revenue ({}) for tenant={} — invoice={} not posted",
                        AR_ACCOUNT_CODE, REVENUE_ACCOUNT_CODE, event.tenantId(), event.invoiceNumber());
                return;
            }

            boolean hasVat = event.vatTotal() != null && event.vatTotal().compareTo(BigDecimal.ZERO) > 0;
            UUID vatAccountId = null;
            if (hasVat) {
                vatAccountId = findAccountByCode(event.tenantId().getValue(), VAT_OUTPUT_ACCOUNT_CODE);
                if (vatAccountId == null) {
                    log.warn("[Accounting] Chart of Accounts missing VAT Output ({}) for tenant={} — invoice={} not posted",
                            VAT_OUTPUT_ACCOUNT_CODE, event.tenantId(), event.invoiceNumber());
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

            // Finance patch F3: once per invoice, so a redelivered event cannot post the invoice twice.
            accountingService.postOnce(event.tenantId(), PostingKeys.of("invoicing", "invoice-issued", event.invoiceId()), req);
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
        try {
            if (event.bankAccountId() == null) {
                // KNOWN GAP, not silently skipped: RecordPaymentRequest's
                // bankAccountId is a new, optional field — the existing
                // frontend doesn't send it yet. Logging clearly rather
                // than guessing a "default" bank account for real money.
                log.warn("[Accounting] Payment recorded for invoice={} tenant={} with no bankAccountId — " +
                                "cannot post a directed payment journal without knowing which account received the funds. " +
                                "Invoice.amountPaid was still updated normally; this is a ledger-posting gap only.",
                        event.invoiceNumber(), event.tenantId());
                return;
            }

            var bankAccount = bankAccountRepo.findActiveById(event.tenantId(), event.bankAccountId()).orElse(null);
            if (bankAccount == null || bankAccount.getAccountId() == null) {
                log.warn("[Accounting] Bank account={} for tenant={} not found or not linked to a Chart of Accounts entry — " +
                        "payment for invoice={} not posted", event.bankAccountId(), event.tenantId(), event.invoiceNumber());
                return;
            }

            UUID arAccountId = findAccountByCode(event.tenantId().getValue(), AR_ACCOUNT_CODE);
            if (arAccountId == null) {
                log.warn("[Accounting] Chart of Accounts missing AR ({}) for tenant={} — payment for invoice={} not posted",
                        AR_ACCOUNT_CODE, event.tenantId(), event.invoiceNumber());
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

            // Finance patch F3: once per payment. Events stored before F3 carry no payment id and post as before.
            if (event.paymentEventId() != null) {
                accountingService.postOnce(event.tenantId(),
                        PostingKeys.of("invoicing", "payment-received", event.paymentEventId()), req);
            } else {
                JournalEntryResponse created = accountingService.createJournalEntry(event.tenantId(), req, null);
                accountingService.postJournalEntry(event.tenantId(), created.id());
            }
            log.info("[Accounting] Posted payment journal for invoice={} tenant={}", event.invoiceNumber(), event.tenantId());
        } catch (Exception e) {
            log.error("[Accounting] Failed to post payment journal for invoice={} tenant={}: {}",
                    event.invoiceNumber(), event.tenantId(), e.getMessage(), e);
        }
    }

    /**
     * Finance patch F1: a credit note reverses part of an invoice's issuance journal. Debit Revenue (subtotal), Debit VAT
     * output (vatTotal), Credit Accounts Receivable (total). Same account codes and the same never-fail-the-source rule as
     * {@link #onInvoiceIssued}.
     */
    @ApplicationModuleListener
    void onCreditNoteIssued(CreditNoteIssuedEvent event) {
        try {
            UUID tenant = event.tenantId().getValue();
            UUID arAccountId = findAccountByCode(tenant, AR_ACCOUNT_CODE);
            UUID revenueAccountId = findAccountByCode(tenant, REVENUE_ACCOUNT_CODE);
            if (arAccountId == null || revenueAccountId == null) {
                log.warn("[Accounting] Chart of Accounts missing AR ({}) or Revenue ({}) for tenant={} — credit note={} not posted",
                        AR_ACCOUNT_CODE, REVENUE_ACCOUNT_CODE, event.tenantId(), event.creditNoteNumber());
                return;
            }
            boolean hasVat = event.vatTotal() != null && event.vatTotal().compareTo(BigDecimal.ZERO) > 0;
            UUID vatAccountId = null;
            if (hasVat) {
                vatAccountId = findAccountByCode(tenant, VAT_OUTPUT_ACCOUNT_CODE);
                if (vatAccountId == null) {
                    log.warn("[Accounting] Chart of Accounts missing VAT Output ({}) for tenant={} — credit note={} not posted",
                            VAT_OUTPUT_ACCOUNT_CODE, event.tenantId(), event.creditNoteNumber());
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

            // Finance patch F3: once per credit note.
            accountingService.postOnce(event.tenantId(), PostingKeys.of("invoicing", "credit-note-issued", event.creditNoteId()), req);
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